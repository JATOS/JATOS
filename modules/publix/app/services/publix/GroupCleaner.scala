package services.publix

import akka.actor.ActorSystem
import daos.common.StudyResultDao
import general.common.{Common, StudyLogger}
import group.{GroupAdministration, GroupDispatcherRegistry}
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.db.jpa.JPAApi

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.compat.java8.FunctionConverters.asJavaSupplier
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
 * This class runs a scheduler that regularly checks for group members that are inactive
 *
 * @author Kristian Lange
 */
@Singleton
class GroupCleaner @Inject()(actorSystem: ActorSystem,
                             lifecycle: ApplicationLifecycle,
                             groupAdministration: GroupAdministration,
                             groupDispatcherRegistry: GroupDispatcherRegistry,
                             studyResultDao: StudyResultDao,
                             publixUtils: PublixUtils,
                             studyLogger: StudyLogger,
                             jpa: JPAApi) {

  private val logger: Logger = Logger(this.getClass)

  def start(): Unit = {
    if (!Common.isGroupsCleaningAllowed) return

    logger.info("Starting group cleaning")
    val task: Runnable = () => jpa.withTransaction(asJavaSupplier(() => findAndRemoveInactiveGroupMembers()))

    implicit val executor: ExecutionContextExecutor = actorSystem.dispatcher
    val scheduler = actorSystem.scheduler.schedule(
      initialDelay = Duration(0, TimeUnit.SECONDS),
      interval = Duration(Common.getGroupsCleaningInterval, TimeUnit.SECONDS),
      runnable = task)

    // Stop the scheduler when the application shuts down.
    lifecycle.addStopHook(() => Future {
      scheduler.cancel()
    })
  }

  /**
   * Finds all group members that are idle AND have no group channel and removes them from the group. Additionally, the
   * study result gets finished with a state FAIL.
   */
  private def findAndRemoveInactiveGroupMembers(): Unit = {
    studyResultDao.findIdleGroupMembers(Common.getGroupsCleaningMemberIdleAfter).forEach(studyResult => {
      if (!groupDispatcherRegistry.hasChannel(studyResult.getId)) {
        val groupResult = studyResult.getActiveGroupResult
        logger.info(s"Force inactive group member with study result ID ${studyResult.getId} to leave its group ${groupResult.getId}.")
        groupAdministration.leave(studyResult)

        publixUtils.finishStudyResult(false, "Inactive group member was forced to leave its group.", studyResult)
        studyLogger.log(studyResult.getStudy, "Finished study run", studyResult.getWorker)
      }
    })
  }

}
