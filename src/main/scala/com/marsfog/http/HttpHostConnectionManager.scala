package com.marsfog.http

import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.apache.commons.pool.{BasePoolableObjectFactory, PoolableObjectFactory}
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{HttpHeaders, HttpRequest, HttpResponse}
import org.jboss.netty.channel.{ExceptionEvent, ChannelStateEvent, Channel, ChannelHandlerContext}
import java.util.NoSuchElementException
import java.util.concurrent.{TimeUnit, ArrayBlockingQueue, Future, Executors}
import org.apache.commons.pool.impl.{HttpConnectionPool, GenericObjectPool}

/**
 * Created by IntelliJ IDEA.
 * User: vgiverts
 * Date: 8/30/11
 * Time: 9:11 PM
 */

class HttpHostConnectionManager(hostId: HostId, config: GenericObjectPool.Config, queueLength: Int, blockIfQueueFull: Boolean) extends HttpResponseHandler {
  val bossThreadPool = Executors.newCachedThreadPool()
  val workerThreadPool = Executors.newCachedThreadPool()
  val bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), workerThreadPool))
  val connectionPool = new HttpConnectionPool(new ChannelFactory, config)
  val queue = new ArrayBlockingQueue[HttpFuture](queueLength, false)
  @volatile var closed = false

  // Set up the event pipeline factory.
  bootstrap.setPipelineFactory(new HttpClientPipelineFactory(hostId.https, this))

  def handleResponse(ctx: ChannelHandlerContext, resp: HttpResponse) {
    ctx.getAttachment.asInstanceOf[HttpFuture].setResult(resp)


    synchronized {
      // If the queue is empty and there is at least 1 other active channel, then return this channel to the pool
      if (queue.size == 0 && connectionPool.getNumActive > 1) {
        connectionPool.returnObject(ctx)
        None
      }
      // Otherwise make this channel available to process the next item in the queue
      else Some(ctx)

    }.map(ctx => {
      // Wait until request is available. Periodically check if this connection manager is closed.
      while (!closed) {
        val future = queue.poll(300, TimeUnit.MILLISECONDS)
        if (future != null) {
          ctx.setAttachment(future)
          future.setChannelCtx(ctx)
          ctx.getChannel.write(future.req)
          return;
        }
      }
    })
  }


  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    // Synchronize on the context to prevent a race condition. See executeRequest(HttpRequest) for details.
    ctx.synchronized {
      if (ctx.getAttachment != null) {
        ctx.getAttachment.asInstanceOf[HttpFuture].setFailed(new RuntimeException("Connection closed."))
        ctx.setAttachment(null)

        // Only return the object if the attachment is NULL, because that means it was actually borrowed.
        connectionPool.returnObject(ctx)
      }
    }
  }


  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    ctx.getChannel.close
    if (ctx.getAttachment != null) {
      ctx.getAttachment.asInstanceOf[HttpFuture].setFailed(e.getCause)
    }
  }

  def executeRequest(req: HttpRequest): HttpFuture = {
    req.setHeader(HttpHeaders.Names.HOST, hostId.host)
    req.setHeader(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP)
    req.setHeader(HttpHeaders.Names.ACCEPT_CHARSET, "utf-8")
    req.setHeader(HttpHeaders.Names.CONNECTION, "keep-alive")
    val future: HttpFuture = new HttpFuture(req)
    try {
      val ctx: ChannelHandlerContext = connectionPool.borrowObject.asInstanceOf[ChannelHandlerContext]

      // Send the request while synchronized on the context. This is to prevent a race condition
      // where the context gets closed just before the attachment is set, which if allowed to
      // happen would prevent the channel from being returned to the pool.
      val requestSent =
        ctx.synchronized {
          if (ctx.getChannel.isConnected) {
            ctx.setAttachment(future)
            ctx.getChannel.write(req)
            true
          }
          // If the channel wasn't connected, then return it to the pool here, since it won't
          // be returned in channelClosed(...) because the attachment has not been set.
          else {
            connectionPool.returnObject(ctx)
            false
          }
        }

      // If we sent the request, then return the future.
      if (requestSent) future

      // Otherwise, retry the request.
      else executeRequest(req)
    }
    catch {
      case e: NoSuchElementException =>
        if (blockIfQueueFull) queue.offer(future)
        else queue.add(future)

        // We just queued the future for future sending, so we can return it now
        future
    }
  }


  class ChannelFactory extends BasePoolableObjectFactory {

    def makeObject() = {
      val future = bootstrap.connect(new InetSocketAddress(hostId.host, hostId.port));
      // Wait until the connection attempt succeeds or fails.

      val channel = future.awaitUninterruptibly().getChannel();
      if (!future.isSuccess()) {
        throw future.getCause()
      }
      channel.getPipeline.getContext(HttpHostConnectionManager.this)
    }

    override def passivateObject(obj: AnyRef) = {
      val ctx = obj.asInstanceOf[ChannelHandlerContext]
      val future = ctx.getAttachment.asInstanceOf[HttpFuture]
      if (future != null) {
        ctx.setAttachment(null)
        future.setChannelCtx(null)
      }
    }

    override def destroyObject(obj: AnyRef) = obj.asInstanceOf[ChannelHandlerContext].getChannel.close

    override def validateObject(obj: AnyRef) = obj.asInstanceOf[ChannelHandlerContext].getChannel.isConnected
  }

  def close = {
    closed = true
    connectionPool.close()
    workerThreadPool.shutdownNow()
    bossThreadPool.shutdownNow()
    bootstrap.releaseExternalResources
  }
}