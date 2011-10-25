package com.marsfog.http

import org.jboss.netty.channel.{Channels, ChannelPipelineFactory}
import org.jboss.netty.handler.ssl.SslHandler
import org.apache.mina.filter.ssl.SslContextFactory
import org.jboss.netty.handler.codec.http.{HttpChunkAggregator, HttpContentDecompressor, HttpClientCodec}

/**
 * Created by IntelliJ IDEA.
 * User: vgiverts
 * Date: 8/30/11
 * Time: 9:16 PM
 */

class HttpClientPipelineFactory(val ssl:Boolean, handler:HttpResponseHandler) extends ChannelPipelineFactory {

      def getPipeline() = {
          // Create a default pipeline implementation.
          val pipeline = Channels.pipeline()

          // Enable HTTPS if necessary.
          if (ssl) {
              val engine = new SslContextFactory().newInstance().createSSLEngine()
              engine.setUseClientMode(true)
              pipeline.addLast("ssl", new SslHandler(engine))
          }

          pipeline.addLast("codec", new HttpClientCodec())
          pipeline.addLast("inflater", new HttpContentDecompressor())
          pipeline.addLast("aggregator", new HttpChunkAggregator(1048576))
          pipeline.addLast("handler", handler)

        pipeline
      }
  }