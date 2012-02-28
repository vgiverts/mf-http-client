package com.marsfog.http

import org.apache.commons.pool.impl.GenericObjectPool
import java.util.concurrent.ConcurrentHashMap
import org.jboss.netty.handler.codec.http._
import org.apache.commons.pool.impl.GenericObjectPool.Config
import collection.mutable.StringBuilder
import java.net.{URLEncoder, URI}
import collection.Seq
import java.lang.String
import java.nio.charset.Charset
import org.jboss.netty.buffer.{ChannelBuffers, ChannelBuffer}

/**
 * Created by IntelliJ IDEA.
 * User: vgiverts
 * Date: 8/30/11
 * Time: 10:33 PM
 */

class HttpClient(maxConnsPerHost: Int, queueLengthPerHost: Int, blockIfQueueFull: Boolean = false) {

  val UTF_8 = Charset.forName("utf-8")
  val hostMgrMap = new ConcurrentHashMap[HostId, DebugHttpHostConnectionManager]
  val config = new GenericObjectPool.Config
  config.maxIdle = maxConnsPerHost
  config.maxActive = maxConnsPerHost
  config.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_FAIL
  config.testOnReturn = true
  config.testOnBorrow = true

  private def getConnMgr(hostId: HostId): DebugHttpHostConnectionManager = {
    var c = hostMgrMap.get(hostId)
    if (c == null) {
      c = new DebugHttpHostConnectionManager(hostId, config, queueLengthPerHost, blockIfQueueFull)
      val oldC = hostMgrMap.putIfAbsent(hostId, c)
      if (oldC != null)
        c = oldC
    }
    c
  }

  private def getHostId(uri: URI): HostId = {
    if (uri.getHost() == null) throw new IllegalArgumentException("The host must be specified. uri: " + uri)
    val host = uri.getHost()

    val scheme = if (uri.getScheme() == null) "http" else uri.getScheme()
    if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
      throw new IllegalArgumentException("Only HTTP(S) is supported.")

    val https: Boolean = scheme.equalsIgnoreCase("https")
    val port = if (uri.getPort() != -1) uri.getPort() else if (https) 443 else 80

    val hostId = new HostId(host, port, https)
    hostId
  }

  def executeParams(url: String, httpMethod: HttpMethod, params: Map[String, String], headers: Map[String, Any] = Map(), cookies: Map[String, String] = Map()) = {
    val contentBytes: Array[Byte] = params.toSeq.map(p => p._1 + '=' + URLEncoder.encode(p._2, "utf-8")).mkString("&").getBytes(UTF_8)
    val extraHeaders = Map(HttpHeaders.Names.CONTENT_TYPE -> "application/x-www-form-urlencoded")
    execute(url, httpMethod, headers ++ extraHeaders, cookies, Some(ChannelBuffers.wrappedBuffer(contentBytes)))
  }

  def execute(url: String, httpMethod: HttpMethod, headers: Map[String, Any] = Map(), cookies: Map[String, String] = Map(), content: Option[ChannelBuffer] = None) = {
    val uri = new URI(url);
    val req: DefaultHttpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, httpMethod, uri.toASCIIString())
    headers.foreach(h => req.setHeader(h._1, h._2))
    if (cookies.size > 0) {
      val cookieEncoder = new CookieEncoder(false);
      cookies.foreach(c => cookieEncoder.addCookie(c._1, c._2))
      req.setHeader(HttpHeaders.Names.COOKIE, cookieEncoder.encode());

    }
    content.map(c => {
      req.setContent(c)
      req.setHeader(HttpHeaders.Names.CONTENT_LENGTH, c.readableBytes)
    })
    getConnMgr(getHostId(uri)).executeRequest(req)
  }

  def shutdown() {
    hostMgrMap.values.toArray(new Array[DebugHttpHostConnectionManager](0)).foreach(_.close)
    hostMgrMap.clear()
  }
}

