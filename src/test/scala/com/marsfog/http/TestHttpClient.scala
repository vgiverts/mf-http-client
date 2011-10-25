package com.marsfog.http

import org.specs._
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}
import org.jboss.netty.handler.codec.http.HttpMethod
import java.nio.charset.Charset

/**
 * Created by IntelliJ IDEA.
 * User: vgiverts
 * Date: 6/15/11
 * Time: 10:07 AM
 */

class TestHttpClient extends Specification {

  val UTF_8 = Charset.forName("utf-8")

  val maxConnsPerHost = 5
  val queueLengthPerHost = 5
  val blockIfQueueFull = false

  val client: HttpClient = new HttpClient(maxConnsPerHost, queueLengthPerHost, blockIfQueueFull);

  "call-google" in {
    val futures = (0 to 9).map(i => client.execute("http://www.google.com/search?q=marsfog", HttpMethod.GET))
    futures.foreach(
      _.get().getContent.toString(UTF_8).contains("marsfog.com") must beTrue
    )
  }

  afterSpec = Some(() => {
    client.shutdown()
  })

}