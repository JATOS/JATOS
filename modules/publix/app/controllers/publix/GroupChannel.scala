package controllers.publix

import akka.actor.{ActorSystem, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import exceptions.publix.{ForbiddenPublixException, PublixException}
import group.{GroupAdministration, GroupChannelActor, GroupDispatcherRegistry}
import models.common.workers._
import models.common.{GroupResult, StudyResult}
import play.api.Logger
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import services.publix.idcookie.IdCookieService
import services.publix.workers._
import services.publix.{PublixUtils, StudyAuthorisation}

import javax.inject.{Inject, Singleton}

/**
 * Abstract class that handles the opening of the group channel. It has concrete implementations for
 * each worker type.
 */
abstract class GroupChannel[A <: Worker](components: ControllerComponents,
                                         publixUtils: PublixUtils,
                                         studyAuthorisation: StudyAuthorisation) extends AbstractController(components) {

  private val logger: Logger = Logger(this.getClass)

  @Inject
  implicit var system: ActorSystem = _

  @Inject
  implicit var materializer: Materializer = _

  @Inject
  var idCookieService: IdCookieService = _

  @Inject
  var groupDispatcherRegistry: GroupDispatcherRegistry = _

  @Inject
  var groupAdministration: GroupAdministration = _

  /**
   * Joins a group but doesn't open the group channel. In case of an error/ problem, a PublixException is thrown.
   * Synchronized to prevent race conditions with group members joining, leaving, reassigning.
   */
  @throws(classOf[PublixException])
  def join(studyResult: StudyResult)(implicit request: RequestHeader): Unit = synchronized {
    logger.info(s".join: studyResult ${studyResult.getId}")
    val worker = studyResult.getWorker.asInstanceOf[A]
    val study = studyResult.getStudy
    val batch = studyResult.getBatch
    studyAuthorisation.checkWorkerAllowedToDoStudy(request.withBody().session.asJava, worker, study, batch)
    publixUtils.checkStudyIsGroupStudy(study)

    if (studyResult.getHistoryGroupResult != null) {
      logger.info(s".join: It's not allowed to join a group after it was explicitly left " +
        s"(studyResult ${studyResult.getId}).")
      throw new ForbiddenPublixException("It's not allowed to join a group after it was explicitly left.")
    }

    if (studyResult.getActiveGroupResult != null)
      logger.info(s".join: studyResult ${studyResult.getId}, workerId ${worker.getId}" +
        s" already member of group ${studyResult.getActiveGroupResult.getId}")
    else {
      val groupResult = groupAdministration.join(studyResult, batch)
      logger.info(s".join: studyResult ${studyResult.getId}, workerId ${worker.getId} " +
        s"joined group ${groupResult.getId}")
    }
  }

  /**
   * Opens a group channel and returns an Akka stream Flow that will be turned into WebSocket. In
   * case of an error/ problem, a PublixException is thrown.
   */
  @throws(classOf[PublixException])
  def open(studyResult: StudyResult): Flow[Any, Nothing, _] = {
    logger.info(s".open: studyResultId ${studyResult.getId}")
    val groupResult: GroupResult = studyResult.getActiveGroupResult

    // To be sure, check if there is already a group channel and close the old one before opening a new one.
    groupAdministration.closeGroupChannel(studyResult.getId, groupResult.getId)

    // Get the GroupDispatcher that will handle this GroupResult.
    val groupDispatcher = groupDispatcherRegistry.getOrRegister(groupResult.getId)
    ActorFlow.actorRef { out => Props(new GroupChannelActor(out, studyResult.getId, groupDispatcher)) }
  }

  /**
   * Tries to reassign this study run (specified by study result ID) to a different group. If the
   * reassignment was successful, an Ok is returned. If it was unsuccessful, a Forbidden is returned.
   * In case of an error/ problem, a PublixException is thrown.
   */
  @throws(classOf[PublixException])
  def reassign(studyResult: StudyResult)(implicit request: Request[_]): Result = synchronized {
    logger.info(s".reassign: studyResultId ${studyResult.getId}")
    val worker = studyResult.getWorker.asInstanceOf[A]
    val study = studyResult.getStudy
    val batch = studyResult.getBatch
    studyAuthorisation.checkWorkerAllowedToDoStudy(request.session.asJava, worker, study, batch)
    publixUtils.checkStudyIsGroupStudy(study)

    if (studyResult.getHistoryGroupResult != null) {
      logger.info(s".reassign: It's not allowed to run a group study twice in the same study run " +
        s"(studyResult ${studyResult.getId}).")
      return Forbidden
    }

    val success = groupAdministration.reassign(studyResult, batch)
    if (!success) {
      NoContent
    } else {
      Ok("")
    }
  }

  /**
   * Let this study run (specified by the study result ID) leave the group that it joined before.
   */
  @throws(classOf[PublixException])
  def leave(studyResult: StudyResult)(implicit request: Request[_]): Result = synchronized {
    logger.info(s".leave: studyResultId ${studyResult.getId}")
    val worker = studyResult.getWorker.asInstanceOf[A]
    val study = studyResult.getStudy
    val batch = studyResult.getBatch
    studyAuthorisation.checkWorkerAllowedToDoStudy(request.session.asJava, worker, study, batch)
    publixUtils.checkStudyIsGroupStudy(study)
    val groupResult = studyResult.getActiveGroupResult
    if (groupResult == null) {
      logger.info(s".leave: studyResult ${studyResult.getId}, workerId ${worker.getId} " +
        s"isn't member of a group - can't leave.")
      return Ok("")
    }

    groupAdministration.leave(studyResult)
    logger.info(s".leave: studyResult ${studyResult.getId}, workerId ${worker.getId} left group ${groupResult.getId}")
    Ok("")
  }

}


@Singleton
class JatosGroupChannel @Inject()(components: ControllerComponents,
                                  publixUtils: PublixUtils,
                                  studyAuthorisation: JatosStudyAuthorisation)
  extends GroupChannel[JatosWorker](components, publixUtils, studyAuthorisation)

@Singleton
class PersonalSingleGroupChannel @Inject()(components: ControllerComponents,
                                           publixUtils: PublixUtils,
                                           studyAuthorisation: PersonalSingleStudyAuthorisation)
  extends GroupChannel[PersonalSingleWorker](components, publixUtils, studyAuthorisation)


@Singleton
class PersonalMultipleGroupChannel @Inject()(components: ControllerComponents,
                                             publixUtils: PublixUtils,
                                             studyAuthorisation: PersonalMultipleStudyAuthorisation)
  extends GroupChannel[PersonalMultipleWorker](components, publixUtils, studyAuthorisation)

@Singleton
class GeneralSingleGroupChannel @Inject()(components: ControllerComponents,
                                          publixUtils: PublixUtils,
                                          studyAuthorisation: GeneralSingleStudyAuthorisation)
  extends GroupChannel[GeneralSingleWorker](components, publixUtils, studyAuthorisation)

@Singleton
class GeneralMultipleGroupChannel @Inject()(components: ControllerComponents,
                                            publixUtils: PublixUtils,
                                            studyAuthorisation: GeneralMultipleStudyAuthorisation)
  extends GroupChannel[GeneralMultipleWorker](components, publixUtils, studyAuthorisation)

// Handles both MTWorker and MTSandboxWorker
@Singleton
class MTGroupChannel @Inject()(components: ControllerComponents,
                               publixUtils: PublixUtils,
                               studyAuthorisation: MTStudyAuthorisation)
  extends GroupChannel[MTWorker](components, publixUtils, studyAuthorisation)
