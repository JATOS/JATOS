package group

import javax.inject.{Inject, Named, Singleton}

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import group.GroupDispatcherRegistry.{Get, GetOrCreate, ItsThisOne}
import group.GroupDispatcher.{Joined, PoisonChannel, ReassignChannel}
import models.common.{GroupResult, StudyResult}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc.{Results, WebSocket}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Service class that handles of opening and closing of group channels with
  * Akka. This class is the interface for using the group channel.
  *
  * @author Kristian Lange (2015, 2017)
  */
@Singleton
class GroupChannelService @Inject()(implicit system: ActorSystem,
                                    materializer: Materializer,
                                    @Named("group-dispatcher-registry-actor") dispatcherRegistry: ActorRef) {

  private val logger: Logger = Logger(this.getClass)

  /**
    * Time to wait for an answer after asking an Akka actor
    */
  implicit val timeout: Timeout = 5.seconds

  /**
    * Opens a new group channel WebSocket for the given StudyResult.
    */
  // TODO put WebSocket creation in Publix when Publix is in Scala
  def openGroupChannel(studyResult: StudyResult): WebSocket = {
    WebSocket.acceptOrResult[JsValue, JsValue] { _ =>
      Future.successful(
        if (studyResult == null)
          Left(Results.InternalServerError)
        else
          try {
            val groupResult: GroupResult = studyResult.getActiveGroupResult
            // Get the GroupDispatcher that will handle this GroupResult.
            val groupDispatcher = getOrCreateDispatcher(groupResult.getId)
            // If this GroupDispatcher already has a group channel for this
            // StudyResult, close the old one before opening a new one.
            closeGroupChannelBlocking(studyResult.getId, groupDispatcher)
            val future = groupDispatcher ? PoisonChannel(studyResult.getId)
            Await.result(future, timeout.duration).asInstanceOf[Boolean]
            Right(ActorFlow.actorRef {
              out => GroupChannelActor.props(out, studyResult.getId, groupDispatcher)
            })
          } catch {
            case e: Exception =>
              logger.error("Exception during opening of group channel", e)
              Left(Results.InternalServerError)
          }
      )
    }
  }

  /**
    * Reassigns the given group channel that is associated with the given
    * StudyResult. It moves the group channel from the current GroupDispatcher
    * to a different one that is associated with the given GroupResult.
    */
  def reassignGroupChannel(studyResult: StudyResult,
                           currentGroupResult: GroupResult,
                           differentGroupResult: GroupResult): Unit = {
    val currentDispatcher = getDispatcher(currentGroupResult.getId).get
    // Get or create, because if the dispatcher was empty it was shutdown
    // and has to be recreated
    val differentDispatcher = getOrCreateDispatcher(differentGroupResult.getId)
    currentDispatcher ! ReassignChannel(studyResult.getId, differentDispatcher)
    currentDispatcher ! GroupDispatcher.Left(studyResult.getId())
    differentDispatcher ! Joined(studyResult.getId)
  }

  /**
    * Close the group channel that belongs to the given StudyResult and
    * GroupResult. It just sends the closing message to the GroupDispatcher
    * without waiting for an answer. We don't use the StudyResult's GroupResult
    * but ask for a separate parameter for the GroupResult because the
    * StudyResult's GroupResult might already be null in the process of leaving
    * a GroupResult.
    */
  def closeGroupChannel(studyResult: StudyResult, groupResult: GroupResult): Unit = {
    val groupDispatcherOption = getDispatcher(groupResult.getId)
    if (groupDispatcherOption.isDefined)
      groupDispatcherOption.get ! PoisonChannel(studyResult.getId)
  }

  /**
    * Sends a message to each member of the group (the GroupResult this
    * studyResult is in). This message tells that this member has joined the
    * GroupResult.
    */
  def sendJoinedMsg(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getActiveGroupResult
    if (groupResult != null) {
      val groupDispatcherOption = getDispatcher(groupResult.getId)
      if (groupDispatcherOption.isDefined)
        groupDispatcherOption.get ! Joined(studyResult.getId)
    }
  }

  /**
    * Sends a message to each member of the GroupResult that this member
    * (specified by StudyResult) has left the GroupResult.
    */
  def sendLeftMsg(studyResult: StudyResult, groupResult: GroupResult): Unit = {
    if (groupResult != null) {
      val groupDispatcherOption = getDispatcher(groupResult.getId)
      if (groupDispatcherOption.isDefined)
        groupDispatcherOption.get ! Left(studyResult.getId)
    }
  }

  /**
    * Closes the group channel that belongs to the given StudyResult and is
    * managed by the given GroupDispatcher. Waits until it receives a result
    * from the GroupDispatcher actor. It returns true if the GroupChannelActor was
    * managed by the GroupDispatcher and was successfully removed from the
    * GroupDispatcher - false otherwise (it was probably never managed by the
    * dispatcher).
    */
  private def closeGroupChannelBlocking(studyResultId: Long,
                                        groupDispatcher: ActorRef): Boolean = {
    val future = groupDispatcher ? PoisonChannel(studyResultId)
    Await.result(future, timeout.duration).asInstanceOf[Boolean]
  }

  /**
    * Get the GroupDispatcher to this GroupResult. The answer is an
    * ActorRef (to a GroupDispatcher).
    */
  private def getDispatcher(groupResultId: Long): Option[ActorRef] = {
    val future = dispatcherRegistry ? Get(groupResultId)
    Await.result(future, timeout.duration).asInstanceOf[ItsThisOne].groupDispatcherOption
  }

  /**
    * Asks the GroupDispatcherRegistry to get or create a group dispatcher for
    * the given ID. It waits until it receives an answer. The answer is an
    * ActorRef (to a GroupDispatcher).
    */
  private def getOrCreateDispatcher(groupResultId: Long): ActorRef = {
    val future = dispatcherRegistry ? GetOrCreate(groupResultId)
    Await.result(future, timeout.duration).asInstanceOf[ItsThisOne].groupDispatcherOption.get
  }

}
