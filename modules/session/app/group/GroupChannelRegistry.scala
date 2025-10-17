package group

import akka.actor.ActorRef
import org.apache.commons.collections4.bidimap.DualHashBidiMap

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * This class stores the members (channels) of a GroupDispatcher. It is essentially a wrapper around a bidirectional
 * map that stores the study result IDs together with their corresponding GroupChannelActors.
 *
 * A Registry does not define who is a member - it just stores the open channels. Therefore, it is possible that a
 * client is a member but currently doesn't have an open channel.
 *
 * @author Kristian Lange
 */
class GroupChannelRegistry {

  /**
   * Contains the members that are handled by a dispatcher. Maps StudyResult's IDs <-> GroupChannelActor.
   * Using a bidirectional map because it's a one-to-one relationship, and we need to be able
   * to quickly get entries via the 'key' and the 'value'.
   */
  private val channelMap = new DualHashBidiMap[Long, GroupChannelActor]

  def register(studyResultId: Long, channel: GroupChannelActor): GroupChannelActor = channelMap.put(studyResultId,
    channel)

  def unregister(studyResultId: Long): Option[GroupChannelActor] = Option(channelMap.remove(studyResultId))

  def getChannel(studyResultId: Long): Option[GroupChannelActor] = Option(channelMap.get(studyResultId))

  def getChannelActor(studyResultId: Long): Option[ActorRef] = {
    val channel = getChannel(studyResultId)
    if (channel.isDefined) {
      Option.apply(channel.get.self)
    } else {
      None
    }
  }

  def isEmpty: Boolean = channelMap.isEmpty

  def containsStudyResult(studyResultId: Long): Boolean = channelMap.containsKey(studyResultId)

  def getAllStudyResultIds: mutable.Set[Long] = channelMap.keySet.asScala

  def getAllChannels: mutable.Set[GroupChannelActor] = channelMap.values.asScala

}