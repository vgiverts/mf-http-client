package com.marsfog.http

import org.apache.commons.pool.impl.GenericObjectPool

/**
 * Created by IntelliJ IDEA.
 * User: vgiverts
 * Date: 8/30/11
 * Time: 10:34 PM
 */

class HttpConfig {

  val config: GenericObjectPool.Config = new GenericObjectPool.Config
  config.maxIdle = -1
  config.testOnBorrow = true
  config.testOnReturn = true
  config.minEvictableIdleTimeMillis

  def setMaxConnectionsPerHost(i:Int) = config.maxActive = i;

  def setMaxConnectionPoolWait(i:Long) = config.maxWait = i
}