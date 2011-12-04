package com.marsfog.http

import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse}
import java.util.concurrent.{ExecutionException, TimeoutException, TimeUnit, Future}
import org.jboss.netty.channel.ChannelHandlerContext

/**
 * Created by IntelliJ IDEA.
 * User: vgiverts
 * Date: 8/30/11
 * Time: 10:20 PM
 */

class HttpFuture(val req: HttpRequest) extends Future[HttpResponse] {

  var done = false
  var response: HttpResponse = null
  var cause: Throwable = null
  var ctx: ChannelHandlerContext = null

  def setChannelCtx(ctx: ChannelHandlerContext) {
    synchronized {
      this.ctx = ctx
    }
  }

  def setFailed(cause: Throwable) {
    synchronized {
      done = true
      this.cause = cause
      notifyAll
    }
  }

  def setResult(response: HttpResponse) {
    synchronized {
      done = true
      this.response = response
      notifyAll
    }
  }

  private def getResponse: HttpResponse = if (response != null) response else throw new ExecutionException(cause)

  def get(timeout: Long, unit: TimeUnit): HttpResponse = {
    synchronized {
      if (done)
        getResponse
      else {
        this.wait(unit.toMillis(timeout))
        if (done)
          getResponse
        else {
          if (ctx != null) ctx.getChannel.close()
          throw new TimeoutException
        }
      }
    }
  }

  def get(): HttpResponse = get(0, TimeUnit.MILLISECONDS)

  def isDone = synchronized {
    done
  }

  def isCancelled = false

  def cancel(mayInterruptIfRunning: Boolean) = false
}