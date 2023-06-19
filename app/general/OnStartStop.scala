package general

import akka.actor.ActorSystem
import daos.common.LoginAttemptDao
import general.common.{Common, JatosUpdater}
import migrations.common.{ComponentResultMigration, MySQLCharsetFix, StudyLinkMigration}
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.db.jpa.JPAApi

import java.io.File
import java.net.{BindException, InetAddress, InetSocketAddress, ServerSocket}
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.compat.java8.FunctionConverters.asJavaSupplier
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
 * Called during start-up
 *
 * @author Kristian Lange
 */
//noinspection ScalaDeprecation
class OnStartStop @Inject()(lifecycle: ApplicationLifecycle,
                            environment: play.Environment,
                            actorSystem: ActorSystem,
                            jpa: JPAApi,
                            jatosUpdater: JatosUpdater,
                            mySQLCharsetFix: MySQLCharsetFix,
                            studyLinkMigration: StudyLinkMigration,
                            componentResultMigration: ComponentResultMigration,
                            loginAttemptDao: LoginAttemptDao) {

  private val logger = Logger(this.getClass)

  if (Common.isMultiNode && !Common.usesMysql()) {
    throw new RuntimeException("You cannot use an H2 database in multi node mode")
  }

  mySQLCharsetFix.run()
  checkUpdate()
  createDirIfNotExist(Common.getStudyAssetsRootPath)
  if (Common.isStudyLogsEnabled) createDirIfNotExist(Common.getStudyLogsPath)
  if (Common.isResultUploadsEnabled) createDirIfNotExist(Common.getResultUploadsPath)
  createDirIfNotExist(Common.getLogsPath)
  createDirIfNotExist(Common.getTmpDir)
  studyLinkMigration.run()
  componentResultMigration.run()
  scheduleLoginAttemptCleaning()

  if (!environment.isProd || Common.isMultiNode) {
    logger.info("JATOS started")
  } else if (!isPortInUse) {
    logger.info("JATOS started")
    println("started")
    println(s"To use JATOS type ${Common.getJatosHttpAddress}:${Common.getJatosHttpPort} in your " +
      s"browser's address bar")
  } else {
    println(s"Error - Could not bind to ${Common.getJatosHttpAddress}:${Common.getJatosHttpPort}")
  }

  lifecycle.addStopHook(() => Future {
    logger.info("JATOS shutdown")
    if (environment.isProd) println("JATOS stopped")
  })

  /**
   * Check if the address (IP:port) is already in use
   * https://stackoverflow.com/a/48828373/1278769
   */
  private def isPortInUse: Boolean = {
    var socket: ServerSocket = null
    try {
      socket = new ServerSocket()
      socket.setReuseAddress(false) // setReuseAddress(false) is required only on OSX
      socket.bind(new InetSocketAddress(InetAddress.getByName(Common.getJatosHttpAddress), Common.getJatosHttpPort), 1)
      false
    } catch {
      case _: BindException =>
        true
    } finally if (socket != null) socket.close()
  }

  /**
   * Logs eventual update messages from the loader script and notify JatosUpdater
   */
  private def checkUpdate(): Unit = {
    if (Common.getJatosUpdateMsg != null) Common.getJatosUpdateMsg match {
      case "success" =>
        jatosUpdater.setUpdateStateSuccess()
        logger.info("JATOS was successfully updated")
      case "update_folder_not_found" =>
        jatosUpdater.setUpdateStateFailed()
        logger.error("JATOS update failed: update folder not found")
      case "more_than_one_update_folder" =>
        jatosUpdater.setUpdateStateFailed()
        logger.error("JATOS update stopped: there is more than one update folder")
      case msg =>
        jatosUpdater.setUpdateStateFailed()
        logger.error(msg)
    }
  }

  private def createDirIfNotExist(path: String): Unit = {
    val dir = new File(path)
    if (!dir.exists()) {
      val success = dir.mkdirs
      if (success) {
        logger.info(".createDirIfNotExist: Created " + path)
      } else {
        logger.error(".createDirIfNotExist: Could not create directory " + path)
      }
    }
  }

  /**
   * Starts a scheduler that cleans the database of old LoginAttempts. It runs every hour and removes all
   * LoginAttempts that are older than 1 minute.
   */
  private def scheduleLoginAttemptCleaning(): Unit = {
    val task: Runnable = () => jpa.withTransaction(asJavaSupplier(() => {
      () => loginAttemptDao.removeOldAttempts()
    }))

    implicit val executor: ExecutionContextExecutor = actorSystem.dispatcher
    val scheduler = actorSystem.scheduler.schedule(
      initialDelay = Duration(0, TimeUnit.SECONDS),
      interval = Duration(1, TimeUnit.HOURS),
      runnable = task)

    lifecycle.addStopHook(() => Future {
      scheduler.cancel()
    })
  }

}
