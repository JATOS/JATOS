package services.publix

import daos.common.StudyResultDao
import general.common.Common
import group.GroupDispatcher
import jakarta.persistence.EntityManager
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import org.apache.pekko.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import org.apache.pekko.pattern.pipe
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.db.jpa.JPAApi

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._

object GroupCleaner {

  private case object Clean
  private case object CleaningFinished
  private case object Stop

  private class CleanerActor(clean: () => Future[Unit], intervalSeconds: Int) extends Actor with ActorLogging {

    implicit private val executionContext: ExecutionContext = context.dispatcher
    private var cleaning = false
    private var stopping = false
    private var scheduler: Cancellable = _

    override def preStart(): Unit = {
      scheduler = context.system.scheduler.scheduleWithFixedDelay(
        initialDelay = Duration.Zero,
        delay = Duration(intervalSeconds, TimeUnit.SECONDS),
        receiver = self,
        message = Clean)
    }

    override def postStop(): Unit = if (scheduler != null) scheduler.cancel()

    override def receive: Receive = {
      case Clean if !cleaning =>
        cleaning = true
        clean().map(_ => CleaningFinished).recover { case e =>
          log.error(e, "Group cleaning failed")
          CleaningFinished
        }.pipeTo(self)
      case Clean => ()
      case CleaningFinished if stopping => context.stop(self)
      case CleaningFinished => cleaning = false
      case Stop if cleaning =>
        stopping = true
        scheduler.cancel()
      case Stop => context.stop(self)
    }
  }

  private def props(clean: () => Future[Unit], intervalSeconds: Int): Props =
    Props(new CleanerActor(clean, intervalSeconds))
}

/**
 * This class runs a scheduler that regularly checks for group members that are inactive
 */
@Singleton
class GroupCleaner @Inject()(actorSystem: ActorSystem,
                             lifecycle: ApplicationLifecycle,
                             groupDispatcher: GroupDispatcher,
                             studyResultDao: StudyResultDao,
                             publixUtils: PublixUtils,
                             jpa: JPAApi) {

  private val logger: Logger = Logger(this.getClass)

  import GroupCleaner._

  def start(): Unit = {
    if (!Common.isGroupsCleaningAllowed) return

    logger.info("Starting group cleaning")
    val cleanerProps = props(
      () => findAndRemoveInactiveGroupMembers(),
      Common.getGroupsCleaningInterval)
    if (Common.isMultiNode) startClusterSingleton(cleanerProps) else {
      val cleaner = actorSystem.actorOf(cleanerProps, "groupCleaner")
      lifecycle.addStopHook(() => Future {
        cleaner ! Stop
      }(actorSystem.dispatcher))
    }
  }

  private def startClusterSingleton(cleanerProps: Props): ActorRef = {
    actorSystem.actorOf(
      ClusterSingletonManager.props(
        singletonProps = cleanerProps,
        terminationMessage = Stop,
        settings = ClusterSingletonManagerSettings(actorSystem)),
      name = "groupCleanerSingletonManager")
  }

  /**
   * Finds all group members that are idle AND have no group channel and removes them from the group. Additionally, the
   * study result gets finished with a state FAIL.
   */
  private def findAndRemoveInactiveGroupMembers(): Future[Unit] = {
    val idleStudyResultIds = jpa.withTransaction((_: EntityManager) => {
      studyResultDao.findIdleGroupMembers(Common.getGroupsCleaningMemberIdleAfter)
    }).asScala.map(_.getId.longValue()).toSeq

    cleanInactiveGroupMembers(idleStudyResultIds)
  }

  private[publix] def cleanInactiveGroupMembers(studyResultIds: Seq[Long]): Future[Unit] = {
    implicit val executor: ExecutionContextExecutor = actorSystem.dispatcher
    Future.sequence(studyResultIds.map(studyResultId =>
      groupDispatcher.hasChannelInCluster(studyResultId).map { hasChannel =>
        if (!hasChannel) {
          logger.info(s"Force inactive group member with study result ID $studyResultId to leave its group.")
          publixUtils.finishStudyRun(
            studyResultId,
            false,
            "Inactive group member was forced to leave its group.",
            "Inactive group member was forced to leave its group. Finished study run.")
        }
      }
    )).map(_ => ())
  }

}
