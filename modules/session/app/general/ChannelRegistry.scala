package general

import akka.actor.ActorRef
import org.apache.commons.collections4.bidimap.DualHashBidiMap

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * This class stores the members (channels) of a Dispatcher. It is essentially a wrapper around
  * a bidirectional map that stores the study result IDs together with their corresponding
  * channels specified by an ActorRef.
  *
  * A Registry does not define who is a member - it just stores the open channels. Therefore, it is
  * possible that a client is a member but currently doesn't have an open channel.
  *
  * @author Kristian Lange
  */
class ChannelRegistry {

  /**
    * Contains the members that are handled by a dispatcher. Maps StudyResult's IDs <-> ActorRefs.
    * Using a bidirectional map because it's a one-to-one relationship, and we need to be able
    * to quickly get entries via the 'key' and the 'value'.
    */
  private val channelMap = new DualHashBidiMap[Long, ActorRef]

  def register(studyResultId: Long, channel: ActorRef): ActorRef = channelMap.put(studyResultId,
    channel)

  def unregister(studyResultId: Long): Option[ActorRef] = Option(channelMap.remove(studyResultId))

  def getChannel(studyResultId: Long): Option[ActorRef] = Option(channelMap.get(studyResultId))

  def isEmpty: Boolean = channelMap.isEmpty

  def containsStudyResult(studyResultId: Long): Boolean = channelMap.containsKey(studyResultId)

  def getAllStudyResultIds: mutable.Set[Long] = channelMap.keySet.asScala

  def getAllChannels: mutable.Set[ActorRef] = channelMap.values.asScala

  def getStudyResult(channel: ActorRef): Option[Long] = Option(channelMap.getKey(channel))

}
