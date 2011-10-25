package org.apache.commons.pool.impl

import org.apache.commons.pool.impl.GenericObjectPool
import org.apache.commons.pool.PoolableObjectFactory

/**
 * Created by IntelliJ IDEA.
 * User: vgiverts
 * Date: 10/25/11
 * Time: 1:45 PM
 */

class HttpConnectionPool(factory: PoolableObjectFactory, config: GenericObjectPool.Config) extends GenericObjectPool(factory, config) {

  override def debugInfo(): String = super.debugInfo();

}