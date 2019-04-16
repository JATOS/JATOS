package controllers.publix

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import exceptions.publix.{ForbiddenPublixException, PublixException}
import group.GroupDispatcher.{JoinedGroup, LeftGroup, PoisonChannel, ReassignChannel}
import group.GroupDispatcherRegistry.{Get, GetOrCreate, ItsThisOne}
import group.{GroupAdministration, GroupChannelActor, GroupDispatcher}
import javax.inject.{Inject, Named, Singleton}
import models.common.workers._
import models.common.{GroupResult, StudyResult}
import play.api.Logger
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import services.publix.idcookie.IdCookieService
import services.publix.workers._
import services.publix.{PublixErrorMessages, PublixUtils, StudyAuthorisation}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Abstract class that handles opening of the group channel. It has concrete implementations for
  * each worker type.
  */
abstract class GroupChannel[A <: Worker](components: ControllerComponents,
                                         publixUtils: PublixUtils[A],
                                         studyAuthorisation:
                                         StudyAuthorisation[A]) extends AbstractController(components) {

  private val logger: Logger = Logger(this.getClass)

  @Inject
  implicit var system: ActorSystem = _

  @Inject
  implicit var materializer: Materializer = _

  @Inject
  var idCookieService: IdCookieService = _

  @Inject
  @Named("group-dispatcher-registry-actor")
  var groupDispatcherRegistry: ActorRef = _

  @Inject
  var groupAdministration: GroupAdministration = _

  /**
    * Time to wait for an answer after asking an Akka actor
    */
  implicit val timeout: Timeout = 5.seconds

  /**
    * Joins a group but doesn't open the group channel. In case of an error/problem an
    * PublixException is thrown.
    */
  @throws(classOf[PublixException])
  def join(studyId: Long, studyResultId: Long): StudyResult = {
    logger.info(s".join: studyId $studyId, studyResultId $studyResultId")
    val idCookie = idCookieService.getIdCookie(studyResultId)
    val worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId)
    val study = publixUtils.retrieveStudy(studyId)
    val batch = publixUtils.retrieveBatch(idCookie.getBatchId)
    studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch)
    publixUtils.checkStudyIsGroupStudy(study)
    val studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId)

    if (studyResult.getHistoryGroupResult != null) {
      logger.info(s".join: It's not allowed to run a group study twice in the same study run " +
          s"(studyId $studyId, studyResultId $studyResultId)."
      )
      throw new ForbiddenPublixException(PublixErrorMessages.GROUP_STUDY_NOT_POSSIBLE_TWICE)
    }

    if (studyResult.getActiveGroupResult != null)
      logger.info(s".join: studyId $studyId, workerId ${idCookie.getWorkerId}" +
          s" already member of group result ${studyResult.getActiveGroupResult.getId}")
    else {
      val groupResult = groupAdministration.join(studyResult, batch)
      sendJoinedMsg(studyResult)
      logger.info(s".join: studyId $studyId, workerId ${idCookie.getWorkerId}" +
          s" joined group result ${groupResult.getId}")
    }

    studyResult
  }

  /**
    * Opens a group channel and returns a Akka stream Flow that will be turned into WebSocket. In
    * case of an error/problem an PublixException is thrown.
    */
  @throws(classOf[PublixException])
  def open(studyResult: StudyResult): Flow[Any, Nothing, _] = {
    logger.info(s".open: studyResultId ${studyResult.getId}")
    val groupResult: GroupResult = studyResult.getActiveGroupResult
    // Get the GroupDispatcher that will handle this GroupResult.
    val groupDispatcher = getOrCreateDispatcher(groupResult.getId)
    // If this GroupDispatcher already has a group channel for this
    // StudyResult, close the old one before opening a new one.
    closeGroupChannelBlocking(studyResult.getId, groupDispatcher)
    ActorFlow.actorRef { out => GroupChannelActor.props(out, studyResult.getId, groupDispatcher) }
  }

  /**
    * Tries to reassign this study run (specified by study result ID) to a different group. If the
    * reassignment was successful an Ok is returned. If it was unsuccessful a Forbidden is returned.
    * In case of an error/problem an PublixException is thrown.
    */
  @throws(classOf[PublixException])
  def reassign(studyId: Long, studyResultId: Long): Result = {
    logger.info(s".reassign: studyId $studyId, studyResultId $studyResultId")
    val idCookie = idCookieService.getIdCookie(studyResultId)
    val worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId)
    val study = publixUtils.retrieveStudy(studyId)
    val batch = publixUtils.retrieveBatch(idCookie.getBatchId)
    studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch)
    publixUtils.checkStudyIsGroupStudy(study)
    val studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId)

    if (studyResult.getHistoryGroupResult != null) {
      logger.info(s".reassign: It's not allowed to run a group study twice in the same study run " +
          s"(studyId $studyId, studyResultId $studyResultId).")
      return Forbidden
    }

    val currentGroupResult = studyResult.getActiveGroupResult
    groupAdministration.reassign(studyResult, batch) match {
      case Left(msg) =>
        logger.info(s".reassign: $msg")
        return Forbidden(msg)
      case Right(differentGroupResult) =>
        reassignGroupChannel(studyResult, currentGroupResult, differentGroupResult)
        logger.info(s".reassign: studyId $studyId, workerId ${idCookie.getWorkerId} reassigned to" +
            s" group result ${differentGroupResult.getId}")
    }
    Ok(" ") // jQuery.ajax cannot handle empty responses
  }

  /**
    * Let this study run (specified by the study result ID) leave the group that it joined before
    */
  @throws(classOf[PublixException])
  def leave(studyId: Long, studyResultId: Long): Result = {
    logger.info(s".leave: studyId $studyId, studyResultId $studyResultId")
    val idCookie = idCookieService.getIdCookie(studyResultId)
    val worker = publixUtils.retrieveTypedWorker(idCookie.getWorkerId)
    val study = publixUtils.retrieveStudy(studyId)
    val batch = publixUtils.retrieveBatch(idCookie.getBatchId)
    studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch)
    val studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId)
    publixUtils.checkStudyIsGroupStudy(study)
    val groupResult = studyResult.getActiveGroupResult
    if (groupResult == null) {
      logger.info(s".leave: studyId $studyId, workerId ${idCookie.getWorkerId} isn't member of a " +
          s"group result - can't leave.")
      return Ok(" ") // jQuery.ajax cannot handle empty responses
    }

    groupAdministration.leave(studyResult)
    closeGroupChannel(studyResult, groupResult)
    logger.info(s".leave: studyId $studyId, workerId ${idCookie.getWorkerId} " +
        s"left group result ${groupResult.getId}")
    Ok(" ") // jQuery.ajax cannot handle empty responses
  }

  /**
    * Closes the group channel which includes sending a left message to all group members.
    */
  def closeGroupChannel(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getActiveGroupResult
    val study = studyResult.getStudy
    if (study.isGroupStudy && groupResult != null)
      closeGroupChannel(studyResult, groupResult)
  }

  /**
    * Close the group channel that belongs to the given StudyResult and GroupResult and tell every
    * group member about it. It just sends the closing message to the GroupDispatcher without
    * waiting for an answer. We don't use the StudyResult's GroupResult but ask for a separate
    * parameter for the GroupResult because the StudyResult's GroupResult might already be null in
    * the process of leaving a GroupResult.
    */
  private def closeGroupChannel(studyResult: StudyResult, groupResult: GroupResult): Unit = {
    val groupDispatcherOption = getDispatcher(groupResult.getId)
    if (groupDispatcherOption.isDefined) {
      groupDispatcherOption.get ! PoisonChannel(studyResult.getId)
      sendLeftMsg(studyResult, groupResult)
    }
  }

  /**
    * Closes the group channel that belongs to the given StudyResult and is managed by the given
    * GroupDispatcher. Waits (and blocks) until it receives a result from the GroupDispatcher actor.
    * It returns true if the GroupChannelActor was managed by the GroupDispatcher and was
    * successfully removed from the GroupDispatcher - false otherwise (it was probably never managed
    * by the dispatcher).
    */
  private def closeGroupChannelBlocking(studyResultId: Long, groupDispatcher: ActorRef): Boolean = {
    val future = groupDispatcher ? PoisonChannel(studyResultId)
    Await.result(future, timeout.duration).asInstanceOf[Boolean]
  }

  /**
    * Sends a message to each member of the group (the GroupResult this studyResult is in). This
    * message tells that this member has joined the GroupResult.
    */
  private def sendJoinedMsg(studyResult: StudyResult): Unit = {
    val groupResult = studyResult.getActiveGroupResult
    if (groupResult != null) {
      val groupDispatcherOption = getDispatcher(groupResult.getId)
      if (groupDispatcherOption.isDefined)
        groupDispatcherOption.get ! JoinedGroup(studyResult.getId)
    }
  }

  /**
    * Sends a message to each member of the GroupResult that this member (specified by StudyResult)
    * has left the GroupResult.
    */
  private def sendLeftMsg(studyResult: StudyResult, groupResult: GroupResult): Unit = {
    if (groupResult != null) {
      val groupDispatcherOption = getDispatcher(groupResult.getId)
      if (groupDispatcherOption.isDefined)
        groupDispatcherOption.get ! LeftGroup(studyResult.getId)
    }
  }

  /**
    * Get the GroupDispatcher to this GroupResult. The answer is an ActorRef (to a GroupDispatcher).
    */
  private def getDispatcher(groupResultId: Long): Option[ActorRef] = {
    val future = groupDispatcherRegistry ? Get(groupResultId)
    Await.result(future, timeout.duration).asInstanceOf[ItsThisOne]
        .groupDispatcherOption
  }

  /**
    * Asks the GroupDispatcherRegistry to get or create a group dispatcher for the given ID. It
    * waits until it receives an answer. The answer is an ActorRef (to a GroupDispatcher).
    */
  private def getOrCreateDispatcher(groupResultId: Long): ActorRef = {
    val future = groupDispatcherRegistry ? GetOrCreate(groupResultId)
    Await.result(future, timeout.duration).asInstanceOf[ItsThisOne]
        .groupDispatcherOption.get
  }

  /**
    * Reassigns the given group channel that is associated with the given StudyResult. It moves the
    * group channel from the current GroupDispatcher to a different one that is associated with the
    * given GroupResult.
    */
  def reassignGroupChannel(studyResult: StudyResult,
                           currentGroupResult: GroupResult,
                           differentGroupResult: GroupResult): Unit = {
    val currentDispatcher = getDispatcher(currentGroupResult.getId).get
    // Get or create, because if the dispatcher was empty it was shutdown
    // and has to be recreated
    val differentDispatcher = getOrCreateDispatcher(differentGroupResult.getId)
    currentDispatcher ! ReassignChannel(studyResult.getId, differentDispatcher)
    currentDispatcher ! GroupDispatcher.LeftGroup(studyResult.getId())
    differentDispatcher ! JoinedGroup(studyResult.getId)
  }

}


@Singleton
class JatosGroupChannel @Inject()(components: ControllerComponents,
                                  publixUtils: JatosPublixUtils,
                                  studyAuthorisation: JatosStudyAuthorisation)
    extends GroupChannel[JatosWorker](components, publixUtils, studyAuthorisation)

@Singleton
class PersonalSingleGroupChannel @Inject()(components: ControllerComponents,
                                           publixUtils: PersonalSinglePublixUtils,
                                           studyAuthorisation: PersonalSingleStudyAuthorisation)
    extends GroupChannel[PersonalSingleWorker](components, publixUtils, studyAuthorisation)


@Singleton
class PersonalMultipleGroupChannel @Inject()(components: ControllerComponents,
                                             publixUtils: PersonalMultiplePublixUtils,
                                             studyAuthorisation: PersonalMultipleStudyAuthorisation)
    extends GroupChannel[PersonalMultipleWorker](components, publixUtils, studyAuthorisation)

@Singleton
class GeneralSingleGroupChannel @Inject()(components: ControllerComponents,
                                          publixUtils: GeneralSinglePublixUtils,
                                          studyAuthorisation: GeneralSingleStudyAuthorisation)
    extends GroupChannel[GeneralSingleWorker](components, publixUtils, studyAuthorisation)

@Singleton
class GeneralMultipleGroupChannel @Inject()(components: ControllerComponents,
                                            publixUtils: GeneralMultiplePublixUtils,
                                            studyAuthorisation: GeneralMultipleStudyAuthorisation)
    extends GroupChannel[GeneralMultipleWorker](components, publixUtils, studyAuthorisation)

// Handles both MTWorker and MTSandboxWorker
@Singleton
class MTGroupChannel @Inject()(components: ControllerComponents,
                               publixUtils: MTPublixUtils,
                               studyAuthorisation: MTStudyAuthorisation)
    extends GroupChannel[MTWorker](components, publixUtils, studyAuthorisation)
