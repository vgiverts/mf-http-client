package com.marsfog.http

import org.jboss.netty.channel.{ChannelHandlerContext, MessageEvent, SimpleChannelUpstreamHandler}
import org.jboss.netty.handler.codec.http.HttpResponse


/**
 * Created by IntelliJ IDEA.
 * User: vgiverts
 * Date: 8/30/11
 * Time: 9:27 PM
 */

trait HttpResponseHandler extends SimpleChannelUpstreamHandler {

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) = handleResponse(ctx, e.getMessage.asInstanceOf[HttpResponse])

  def handleResponse(ctx: ChannelHandlerContext, response: HttpResponse)
}
