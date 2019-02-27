package general

import java.io.File

import daos.common.UserDao
import general.common.Common
import javax.inject.Inject
import models.common.User.Role
import play.api.Logger
import play.db.jpa.JPAApi
import services.gui.UserService

import scala.compat.java8.FunctionConverters._

/**
  * This Initializer is called once with every start of JATOS and does some JATOS
  * specific initialisation.
  *
  * @author Kristian Lange
  */
class Initializer @Inject()(jpa: JPAApi, userDao: UserDao, userService: UserService) {

  private val logger: Logger = Logger(this.getClass)

  checkUpdate()
  checkAdmin()
  checkStudyAssetsRootDir()
  logger.info("JATOS initialized")

  /**
    * Logs eventual update messages from the loader script
    */
  private def checkUpdate() {
    if (Common.getJatosUpdateMsg != null) Common.getJatosUpdateMsg match {
      case "success" => logger.info("JATOS was successfully updated")
      case "update_folder_not_found" => logger.error("JATOS update failed: update folder not found")
      case "more_than_one_update_folder" => logger.error("JATOS update stopped: there is more than one " +
          "update folder")
      case msg => logger.error(msg)
    }
  }

  /**
    * Check for user admin: In case the application is started the first time
    * we need an initial user: admin. If admin can't be found, create one.
    */
  private def checkAdmin() {
    jpa.withTransaction(asJavaSupplier(() => {
      var admin = userDao.findByEmail(UserService.ADMIN_EMAIL)
      if (admin == null) admin = userService.createAndPersistAdmin

      // Some older JATOS versions miss the ADMIN role
      if (!admin.hasRole(Role.ADMIN)) {
        admin.addRole(Role.ADMIN)
        userDao.update(admin)
      }
    }))
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
