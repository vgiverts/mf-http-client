package com.marsfog.http

import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse}
import java.util.concurrent.{ExecutionException, TimeoutException, TimeUnit, Future}

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

  def getResponse: HttpResponse = if (response != null) response else throw new ExecutionException(cause)

  def get(timeout: Long, unit: TimeUnit):HttpResponse = {
    synchronized {
      if (done)
        getResponse
      else {
        this.wait(unit.toMillis(timeout))
        if (done)
          getResponse
        else
          throw new TimeoutException
      }
    }
  }

  def get():HttpResponse = get(0, TimeUnit.MILLISECONDS)

  def isDone = synchronized { done }

  def isCancelled = false

  def cancel(mayInterruptIfRunning: Boolean) = false
}