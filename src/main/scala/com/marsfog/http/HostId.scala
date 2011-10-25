package com.marsfog.http

import org.apache.commons.pool.impl.GenericObjectPool
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by IntelliJ IDEA.
 * User: vgiverts
 * Date: 8/30/11
 * Time: 10:33 PM
 */

class HostId(val host:String, val port:Int, val https:Boolean) {
  override def equals(obj: Any) = {
    val id: HostId = obj.asInstanceOf[HostId]
    id.host == host && id.port == port && id.https == https
  }

  override def hashCode = host.hashCode ^ port.hashCode ^ https.hashCode
}

