package batch

import play.api.Logger

import javax.inject.{Inject, Singleton}
import scala.collection.mutable

/**
 * The BatchDispatcherRegistry keeps track of all BatchDispatchers.
 *
 * @author Kristian Lange
 */
@Singleton
class BatchDispatcherRegistry @Inject()(batchDispatcherFactory: BatchDispatcher.Factory) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * Contains the dispatchers that are currently registered. Maps a batch ID to the BatchDispatcher.
   */
  private val dispatcherMap = mutable.HashMap[Long, BatchDispatcher]()

  /*
   * Get a BatchDispatcher for a particular batch ID. Returns None if no BatchDispatcher is registered.
   */
  def get(batchid: Long): Option[BatchDispatcher] = dispatcherMap.get(batchid)

  /*
   * Get or register a BatchDispatcher for a particular batch ID.
   */
  def getOrRegister(batchId: Long): BatchDispatcher = synchronized {
    if (!dispatcherMap.contains(batchId)) {
      val dispatcher = batchDispatcherFactory.create(batchId)
      dispatcherMap += (batchId -> dispatcher)
      logger.debug(s".getOrRegister: registered dispatcher for batch ID $batchId")
    }
    dispatcherMap(batchId)
  }

  /*
   * Unregister a BatchDispatcher for a particular batch ID.
   */
  def unregister(batchId: Long): Unit = synchronized {
    if (dispatcherMap.contains(batchId)) {
      dispatcherMap -= batchId
      logger.debug(s".unregister: unregistered dispatcher for batch ID $batchId")
    } else {
      logger.debug(s".unregister: dispatcher for batch ID $batchId not found")
    }
  }

}
