package controllers.publix

import java.io.{File, IOException}

import daos.common.{ComponentDao, StudyDao}
import exceptions.publix.{ForbiddenPublixException, NotFoundPublixException, PublixException}
import general.common.{Common, MessagesStrings}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc.{Action, Controller, Result}
import play.db.jpa.JPAApi
import services.publix.PublixErrorMessages
import services.publix.idcookie.IdCookieService
import utils.common.{HttpUtils, IOUtils}

import scala.compat.java8.FunctionConverters.asJavaSupplier

/**
  * Manages web-access to files in the external study assets directories (outside of JATOS'
  * packed Jar).
  *
  * @author Kristian Lange
  */
@Singleton
class StudyAssets @Inject()(ioUtils: IOUtils, idCookieService: IdCookieService, jpa: JPAApi,
                            studyDao: StudyDao, componentDao: ComponentDao) extends Controller {

  private val logger: Logger = Logger(this.getClass)

  private val URL_PATH_SEPARATOR = "/"

  /**
    * Identifying part of any URL that indicates an access to the study assets directories.
    */
  val URL_STUDY_ASSETS = "study_assets"

  val jatosPublixPattern = "(.*)(jatos-publix/javascripts/)(.*)".r

  /**
    * Returns the study asset file that belongs to the study with the given study ID (component ID
    * is ignored) and has the given relative path within the study assets folder. In difference to
    * the viaAssetsPath method it is not necessary to add the prefix 'study_assets' or the study
    * assets folder name (because it's retrieved from the DB).
    * Additionally this method can be used to get jatos.js and other javascripts from JATOS.
    */
  def viaStudyPath(studyId: Long, componentId: Long, urlPath: String) =
    urlPath match {
      case "jatos.js" => controllers.Assets.at(path =
          "/public/lib/jatos-publix/javascripts", file = "jatos.js")
      case jatosPublixPattern(pathPrefix, identifier, file) => controllers.Assets.at(path =
          "/public/lib/jatos-publix/javascripts", file)
      case _ => jpa.withTransaction(asJavaSupplier(() => {
        val study = studyDao.findById(studyId)
        if (study == null) {
          BadRequest(MessagesStrings.studyNotExist(studyId))
        }
        viaAssetsPath(study.getDirName() + URL_PATH_SEPARATOR + urlPath)
      }))
    }

  /**
    * Action called while routing. Translates the given file path from the URL into a file path
    * of the OS's file system and returns the file.
    */
  def viaAssetsPath(urlPath: String) = Action { request =>
    // Set Http.Context used in Play with Java. Needed by IdCookieService
    play.mvc.Http.Context.current.set(play.core.j.JavaHelpers.createJavaContext(request))

    try {
      checkProperAssets(urlPath)
      val filePath = urlPath.replace(URL_PATH_SEPARATOR, File.separator)
      val file = ioUtils.getExistingFileSecurely(Common.getStudyAssetsRootPath, filePath)
      logger.debug(s".viaAssetsPath: loading file ${file.getPath}.")
      Ok.sendFile(file, true).withHeaders("Cache-Control" -> "private")
    } catch {
      case e: PublixException =>
        val errorMsg = e.getMessage
        logger.info(".viaAssetsPath: " + errorMsg)
        if (HttpUtils.isAjax) Forbidden(errorMsg)
        else Forbidden(views.html.publix.error.render(errorMsg))
      case e: IOException =>
        logger.info(s".viaAssetsPath: failed loading from path ${Common.getStudyAssetsRootPath}" +
            s"${File.separator}$urlPath")
        val errorMsg = s"Resource '$urlPath' couldn't be found."
        if (HttpUtils.isAjax) NotFound(errorMsg)
        else NotFound(views.html.publix.error.render(errorMsg))
    }
  }

  /**
    * Throws a ForbiddenPublixException if this request is not allowed to access the study assets
    * given in the URL path. It compares the study assets that are within the given filePath with
    * all study assets that are stored in the JATOS ID cookies. If at least one of them has the
    * study assets then the filePath is allowed. If not a ForbiddenPublixException is thrown.
    *
    * Drawback: It can't compare with the ID cookie that actually belongs to this study run since
    * it has no way of find out which it is (we have no study result ID). But since all ID cookie
    * originate in the same browser one can assume this worker is allowed to access the study
    * assets.
    */
  @throws[PublixException]
  private def checkProperAssets(urlPath: String): Unit = {
    val filePathArray = urlPath.split(URL_PATH_SEPARATOR)
    if (filePathArray.isEmpty)
      throw new ForbiddenPublixException(
        PublixErrorMessages.studyAssetsNotAllowedOutsideRun(urlPath))
    val studyAssets = filePathArray(0)
    if (!idCookieService.oneIdCookieHasThisStudyAssets(studyAssets))
      throw new ForbiddenPublixException(
        PublixErrorMessages.studyAssetsNotAllowedOutsideRun(urlPath))
  }

  /**
    * Retrieves the component's HTML file from the study assets
    */
  @throws[NotFoundPublixException]
  def retrieveComponentHtmlFile(studyDirName: String, componentHtmlFilePath: String): Result = {
    try {
      val file = ioUtils.getFileInStudyAssetsDir(studyDirName, componentHtmlFilePath)
      Ok.sendFile(file, true).as("text/html; charset=utf-8")
          .withHeaders("Cache-Control" -> "no-cache, no-store")
    } catch {
      case e: IOException =>
        throw new NotFoundPublixException(
          MessagesStrings.htmlFilePathNotExist(studyDirName, componentHtmlFilePath))
    }
  }

}
