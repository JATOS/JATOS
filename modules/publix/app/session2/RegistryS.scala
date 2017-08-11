package session2

import akka.actor.ActorRef
import org.apache.commons.collections4.bidimap.DualHashBidiMap

/**
  * This class stores the members of a Dispatcher. It's mostly a wrapper around a
  * map that stores the study result IDs together with their corresponding
  * channels specified by an ActorRef.
  *
  * A Registry does not define who is member - it just stores the open channels.
  * It's possible that a client is a member but currently doesn't have an open
  * channel.
  *
  * @author Kristian Lange (2016, 2017)
  */
class RegistryS {

  /**
    * Contains the members that are handled by a dispatcher. Maps StudyResult's
    * IDs <-> ActorRefs. Using a bidirectional map because it's a one-to-one
    * relationship and we need to be able to quickly get entries via the 'key'
    * and the 'value'.
    */
  private val channelMap = new DualHashBidiMap[Long, ActorRef]

  def register(studyResultId: Long, channel: ActorRef) = channelMap.put(studyResultId, channel)

  def unregister(studyResultId: Long) = channelMap.remove(studyResultId)

  def getChannel(studyResultId: Long): ActorRef = channelMap.get(studyResultId)

  def isEmpty: Boolean = channelMap.isEmpty

  def containsStudyResult(studyResultId: Long): Boolean = channelMap.containsKey(studyResultId)

  // TODO switch to Scala types
  def getAllStudyResultIds: java.util.Set[java.lang.Long] = channelMap.keySet.asInstanceOf[java.util.Set[java.lang.Long]]

  // TODO switch to Scala types
  def getAllChannels: java.util.Collection[ActorRef] = channelMap.values

  def getStudyResult(channel: ActorRef): Long = channelMap.getKey(channel)

}
