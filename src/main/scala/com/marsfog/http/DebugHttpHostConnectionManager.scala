package com.marsfog.http

import org.jboss.netty.bootstrap.ClientBootstrap
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.apache.commons.pool.BasePoolableObjectFactory
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{HttpHeaders, HttpRequest, HttpResponse}
import org.jboss.netty.channel.{ExceptionEvent, ChannelStateEvent, ChannelHandlerContext}
import java.util.NoSuchElementException
import java.util.concurrent.{TimeUnit, ArrayBlockingQueue, Executors}
import org.apache.commons.pool.impl.{HttpConnectionPool, GenericObjectPool}

/**
 * Created by IntelliJ IDEA.
 * User: vgiverts
 * Date: 8/30/11
 * Time: 9:11 PM
 */

class DebugHttpHostConnectionManager(hostId: HostId, config: GenericObjectPool.Config, queueLength: Int, blockIfQueueFull: Boolean) extends HttpResponseHandler {
  val bossThreadPool = Executors.newCachedThreadPool()
  val workerThreadPool = Executors.newCachedThreadPool()
  val bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), workerThreadPool))
  val queue = new ArrayBlockingQueue[HttpFuture](queueLength, false)
  @volatile var closed = false

  // Set up the event pipeline factory.
  bootstrap.setPipelineFactory(new HttpClientPipelineFactory(hostId.https, this))

  def handleResponse(ctx: ChannelHandlerContext, resp: HttpResponse) {
    ctx.getAttachment.asInstanceOf[HttpFuture].setResult(resp)
    ctx.getChannel.close()
  }


  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    // Synchronize on the context to prevent a race condition. See executeRequest(HttpRequest) for details.
    ctx.synchronized {
      if (ctx.getAttachment != null) {
        ctx.getAttachment.asInstanceOf[HttpFuture].setFailed(new RuntimeException("Connection closed."))
        ctx.setAttachment(null)
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
      val ctx = new ChannelFactory().makeObject().asInstanceOf[ChannelHandlerContext]

      // Send the request while synchronized on the context. This is to prevent a race condition
      // where the context gets closed just before the attachment is set, which if allowed to
      // happen would prevent the channel from being returned to the pool.
      val requestSent =
        ctx.synchronized {
          if (ctx.getChannel.isConnected) {
            ctx.setAttachment(future)
            ctx.getChannel.write(req).awaitUninterruptibly()
            true
          }
          // If the channel wasn't connected, then return it to the pool here, since it won't
          // be returned in channelClosed(...) because the attachment has not been set.
          else {
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
      channel.getPipeline.getContext(DebugHttpHostConnectionManager.this)
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
    workerThreadPool.shutdownNow()
    bossThreadPool.shutdownNow()
    bootstrap.releaseExternalResources
  }
}