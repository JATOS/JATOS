package batch

import akka.actor.ActorRef
import org.apache.commons.collections4.bidimap.DualHashBidiMap

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
  * This class stores the members (channels) of a BatchDispatcher. It is essentially a wrapper around
  * a bidirectional map that stores the study result IDs together with their corresponding
  * BatchChannelActors specified by an ActorRef.
  *
  * A Registry does not define who is a member - it just stores the open channels.
  *
  * @author Kristian Lange
  */
class BatchChannelRegistry {

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

}