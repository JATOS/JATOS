package general

import general.common.{Common, JatosUpdater}
import play.api.Logger
import play.api.inject.ApplicationLifecycle

import java.io.File
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Called during start-up
  *
  * @author Kristian Lange
  */
class OnStartStop @Inject()(lifecycle: ApplicationLifecycle,
                            environment: play.Environment,
                            jatosUpdater: JatosUpdater,
                            mySQLCharsetFix: MySQLCharsetFix) {

  private val logger = Logger(this.getClass)

  mySQLCharsetFix.run()
  checkUpdate()
  checkStudyAssetsRootDir()

  logger.info("JATOS started")
  if (environment.isProd) {
    println("started")
    println(s"To use JATOS type ${Common.getJatosHttpAddress}:${Common.getJatosHttpPort} in your " +
      s"browser's address bar")
  }

  lifecycle.addStopHook(() => Future {
    logger.info("JATOS shutdown")
    if (environment.isProd) println("JATOS stopped")
  })

  /**
    * Logs eventual update messages from the loader script and notify JatosUpdater
    */
  private def checkUpdate() {
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

  /**
    * Check whether studies assets root directory exists and create if not.
    */
  private def checkStudyAssetsRootDir() {
    val studyAssetsRoot = new File(Common.getStudyAssetsRootPath)
    val success = studyAssetsRoot.mkdirs
    if (success) logger.info(".checkStudyAssetsRootDir: Created study assets root directory " +
      Common.getStudyAssetsRootPath)
    if (!studyAssetsRoot.isDirectory) logger.error(".checkStudyAssetsRootDir: Study assets root " +
      "directory " + Common.getStudyAssetsRootPath + " couldn't be created.")
  }

}
