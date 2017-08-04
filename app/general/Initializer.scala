package general

import java.io.File
import javax.inject.Inject

import daos.common.UserDao
import general.common.Common
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

  checkAdmin()
  checkStudyAssetsRootDir()
  logger.info("JATOS initialized")

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
    if (success) logger.info(".checkStudyAssetsRootDir: Created study assets root directory " + Common.getStudyAssetsRootPath)
    if (!studyAssetsRoot.isDirectory) logger.error(".checkStudyAssetsRootDir: Study assets root directory " + Common.getStudyAssetsRootPath + " couldn't be created.")
  }

}
