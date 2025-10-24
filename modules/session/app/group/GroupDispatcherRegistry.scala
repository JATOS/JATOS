package group

import play.api.Logger

import javax.inject.{Inject, Singleton}
import scala.collection.mutable
import scala.language.postfixOps

/**
 * The GroupDispatcherRegistry keeps track of all GroupDispatchers.
 *
 * @author Kristian Lange
 */
@Singleton
class GroupDispatcherRegistry @Inject()(groupDispatcherFactory: GroupDispatcher.Factory) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * Contains the dispatchers that are currently registered. Maps a group result ID to the GroupDispatcher.
   */
  private val dispatcherMap = mutable.HashMap[Long, GroupDispatcher]()

  /*
 * Get a GroupDispatcher for a particular batch ID. Returns None if no GroupDispatcher is registered.
 */
  def get(groupResultId: Long): Option[GroupDispatcher] = dispatcherMap.get(groupResultId)

  /**
   * Checks if one of the group dispatchers has a channel with the given study result ID.
   */
  def hasChannel(studyResultId: Long): Boolean = dispatcherMap.values.exists(_.hasChannel(studyResultId))

  /*
   * Get or register a GroupDispatcher for a particular group result ID.
   */
  def getOrRegister(groupResultId: Long): GroupDispatcher = synchronized {
    if (!dispatcherMap.contains(groupResultId)) {
      val dispatcher = groupDispatcherFactory.create(groupResultId)
      dispatcherMap += (groupResultId -> dispatcher)
      logger.debug(s".getOrRegister: registered dispatcher for group result ID $groupResultId")
    }
    dispatcherMap(groupResultId)
  }

  /*
 * Unregister a GroupDispatcher for a particular group result ID.
 */
  def unregister(groupResultId: Long): Unit = synchronized {
    if (dispatcherMap.contains(groupResultId)) {
      dispatcherMap -= groupResultId
      logger.debug(s".unregister: registered dispatcher for group result ID $groupResultId")
    } else {
      logger.debug(s".unregister: dispatcher for group result ID $groupResultId not found")
    }
  }

}
