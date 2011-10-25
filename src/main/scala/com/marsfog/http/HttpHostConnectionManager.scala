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
          ctx.getChannel.write(future.req)
          return;
        }
      }
    })
  }


  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    if (ctx.getAttachment != null) {
      ctx.getAttachment.asInstanceOf[HttpFuture].setFailed(new RuntimeException("Connection closed."))
      ctx.setAttachment(null)
      connectionPool.returnObject(ctx)
    }
  }


  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    ctx.getChannel.close
    if (ctx.getAttachment != null) {
      ctx.getAttachment.asInstanceOf[HttpFuture].setFailed(e.getCause)
      ctx.setAttachment(null)
      connectionPool.returnObject(ctx)
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
      ctx.setAttachment(future)
      ctx.getChannel.write(req)
    }
    catch {
      case e: NoSuchElementException =>
        if (blockIfQueueFull) queue.offer(future)
        else queue.add(future)
    }
    future
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

    override def passivateObject(obj: AnyRef) = obj.asInstanceOf[ChannelHandlerContext].setAttachment(null)

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