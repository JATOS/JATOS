package controllers.publix

import daos.common.StudyResultDao
import exceptions.common.{BadRequestException, ForbiddenException, NotFoundException}
import executor.common.StudyAssetsExecutor
import filters.publix.IdCookieFilter.IdCookies
import http.common.Http.Context
import general.common.{Common, MessagesStrings}
import http.common.HttpUtils
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import play.db.jpa.JPAApi
import services.publix.idcookie.IdCookieService
import services.publix.{PublixErrorMessages, PublixHelpers}
import utils.common.{StringUtils, IOUtils}

import java.io.{File, IOException}
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.{Inject, Singleton}
import javax.persistence.EntityManager
import scala.annotation.unused
import scala.compat.java8.FunctionConverters.asJavaFunction
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex

/**
 * Manages web-access to files in the external study assets directories (outside of JATOS' packed Jar).
 */
@Singleton
class StudyAssets @Inject()(components: ControllerComponents,
                            ioUtils: IOUtils,
                            idCookieService: IdCookieService,
                            jpa: JPAApi,
                            studyResultDao: StudyResultDao,
                            assets: Assets,
                            studyAssetsExecutor: StudyAssetsExecutor) extends AbstractController(components) {

  private val logger: Logger = Logger(this.getClass)

  private val URL_PATH_SEPARATOR = "/"

  private val jatosPublixPattern: Regex = "(.*)(jatos-publix)(.*)".r

  /**
   * Returns the study asset file that belongs to the study with the given study result UUID
   * and has the given relative path within the study assets folder. In difference to
   * the viaAssetsPath method, it is not necessary to add the prefix 'study_assets' or the study
   * assets folder name (because it's retrieved from the DB).
   * Additionally, this method can be used to get jatos.js and other JavaScript files from JATOS.
   * The parameter componentUuid is never used but can't be removed.
   */
  @IdCookies
  def viaStudyPath(studyResultUuid: String, @unused componentUuid: String, urlPath: String): Action[AnyContent] =
    Action.async { implicit request =>
      urlPath match {
        case "jatos.js" => assets.at(path = "/public/lib/jatos-publix/javascripts", file = "jatos.js")(request)
        case "jatos.min.js" => assets.at(path = "/public/lib/jatos-publix/javascripts", file = "jatos.min.js")(request)
        case jatosPublixPattern(_, _, file) => assets.at(path = "/public/lib/jatos-publix", file)(request)
        case _ => Context.withContext(studyAssetsExecutor, () => {
          jpa.withTransaction(asJavaFunction((_: EntityManager) => {
            val studyResult = studyResultDao.findByUuid(studyResultUuid).orElseThrow(() =>
              new BadRequestException("A study result " + studyResultUuid + " doesn't exist."))
            val urlDecodedPath = URLDecoder.decode(studyResult.getStudy.getDirName + URL_PATH_SEPARATOR + urlPath, StandardCharsets.UTF_8.name())
            sendAssetFile(request, urlDecodedPath)
          }))
        }).toScala
      }
    }

  /**
    * Action called while routing. Translates the given file path from the URL into a file path
    * of the OS's file system and returns the file.
    */
  @IdCookies
  def viaAssetsPath(urlPath: String): Action[AnyContent] =
    Action.async { implicit request =>
      Context.withContext(studyAssetsExecutor, () => {
        val urlDecodedPath = URLDecoder.decode(urlPath, StandardCharsets.UTF_8.name())
        sendAssetFile(request, urlDecodedPath)
      }).toScala
    }

  /**
   * Shared logic to find and render a file from the study assets
   */
  private def sendAssetFile(request: Request[AnyContent], urlPath: String): Result = {
    try {
      checkProperAssets(urlPath)
      val file = ioUtils.getExistingFileSecurely(Common.getStudyAssetsRootPath, filePath)
      logger.debug(s".viaAssetsPath: loading file $file.")
      if (request.headers.hasHeader(RANGE)) {
        RangeResult.ofPath(file, request.headers.get(RANGE), Option.empty)
      } else {
        Context.current().response.setHeader("Cache-Control", "private")
        Ok.sendPath(file, inline = true)
      }
    } catch {
      case e: ForbiddenException =>
        val errorMsg = e.getMessage
        logger.info(".viaAssetsPath: " + errorMsg)
        if (HttpUtils.isHtmlRequest) Forbidden(views.html.publix.error.render(errorMsg))
        else Forbidden(errorMsg)
      case _: IOException =>
        logger.info(s".viaAssetsPath: failed loading from path ${Common.getStudyAssetsRootPath}${File.separator}$filePath")
        val errorMsg = s"Resource '$filePath' couldn't be found."
        if (HttpUtils.isHtmlRequest) NotFound(views.html.publix.error.render(errorMsg))
        else NotFound(errorMsg)
    }
  }

  /**
   * Throws a ForbiddenPublixException if this request is not allowed to access the study assets
   * given in the URL path. It compares the study assets that are within the given filePath with
   * all study assets that are stored in the JATOS ID cookies. If at least one of them has the
   * study assets, then the filePath is allowed. If not, a ForbiddenPublixException is thrown.
   *
   * Drawback: It can't compare with the ID cookie that actually belongs to this study run since
   * it has no way of finding out which it is (we have no study result ID). But since all ID cookies
   * originate in the same browser, one can assume this worker is allowed to access the study
   * assets.
   */
  private def checkProperAssets(urlPath: String): Unit = {
    val filePathArray = urlPath.split(URL_PATH_SEPARATOR)
    if (filePathArray.isEmpty)
      throw new ForbiddenException(PublixErrorMessages.studyAssetsNotAllowedOutsideRun(urlPath))
    val studyAssets =filePathArray(0)
    if (!idCookieService.oneIdCookieHasThisStudyAssets(studyAssets))
      throw new ForbiddenException(PublixErrorMessages.studyAssetsNotAllowedOutsideRun(urlPath))
  }

  /**
   * Retrieves the component's HTML file from the study assets
   */
  def retrieveComponentHtmlFile(studyDirName: String, componentHtmlFilePath: String): Result = {
    try {
      val file = ioUtils.getFileInStudyAssetsDir(studyDirName, componentHtmlFilePath)
      Context.current().response().setHeader("Cache-Control", "no-cache, no-store")
      Context.current().response().setHeader("Content-Type", "text/html; charset=utf-8")
      Ok.sendPath(file)
    } catch {
      case _: IOException =>
        throw new NotFoundException(MessagesStrings.htmlFilePathNotExist(studyDirName, componentHtmlFilePath))
    }
  }

  /**
   * Redirects to or shows the end page (either from study assets or default end page) after a study run finished.
   * Passes on the confirmationCode in case it's defined (either cookie or URL query parameter).
   */
  def endPage(studyResultUuid: String, confirmationCode: Option[String] = None): Action[AnyContent] =
    Action.async { _ =>
      Context.withContext(studyAssetsExecutor, () => {
        jpa.withTransaction(asJavaFunction((_: EntityManager) => {
          val studyResult = studyResultDao.findByUuid(studyResultUuid).orElseThrow(
            () => new BadRequestException("A study result " + studyResultUuid + " doesn't exist."))
          if (!PublixHelpers.studyRunDone(studyResult)) {
            throw new BadRequestException("The study result " + studyResultUuid + " isn't finished yet.")
          }

          else if (studyResult.getStudy.getEndRedirectUrl != null && studyResult.getStudy.getEndRedirectUrl.trim() != "") {
            // Redirect to URL specified in study properties
            val endRedirectUrl = enhanceQueryStringInEndRedirectUrl(studyResult.getUrlQueryParameters,
              studyResult.getStudy.getEndRedirectUrl)
            confirmationCode match {
              case Some(cc) => Redirect(endRedirectUrl, Map("confirmationCode" -> Seq(cc)))
              case None => Redirect(endRedirectUrl)
            }
          }

          else if (ioUtils.checkFileInStudyAssetsDirExists(studyResult.getStudy.getDirName, "endPage.html")) {
            // Redirect to endPage.html from study assets
            confirmationCode match {
              case Some(cc) =>
                Context.current().response().setCookie(confirmationCodeCookie(cc).asJava)
                Ok.sendPath(ioUtils.getExistingFileInStudyAssetsDir(studyResult.getStudy.getDirName, "endPage.html"))
              case None => Ok.sendPath(ioUtils
                .getExistingFileInStudyAssetsDir(studyResult.getStudy.getDirName, "endPage.html"))
            }
          }

          else {
            // Return default end page
            confirmationCode match {
              case Some(cc) => Ok(views.html.publix.confirmationCode.render(cc))
              case None => Ok(views.html.publix.endPage.render())
            }
          }
        }))
      }).toScala
    }

  /**
   * Exchange arguments in endRedirectUrl with the ones provided in urlQueryParameters.
   *
   * Example:
   * Original study link: https://myjatosdomain/publix/1/start?batchId=1&personalSingleWorkerId=1234&SONA_ID=123abc
   * urlQueryParameters: {"batchId": "1", "personalSingleWorkerId": "1234", "SONA_ID": "123abc"}
   * endRedirectUrl: https://my.redirect.url/somepath?foo=100&survey_id=[SONA_ID]
   * Will return: https://my.redirect.url/somepath?foo=100&survey_id=123abc
   *
   * @param urlQueryParameters URL query parameters from the original study run URL (URL decoded)
   * @param endRedirectUrl     URL that will be used to redirect (URL encoded)
   * @return
   */
  def enhanceQueryStringInEndRedirectUrl(urlQueryParameters: String, endRedirectUrl: String): String = {
    val originalStudyLinkUrlQueryParameters = Json.parse(urlQueryParameters)
    var newEndRedirectUrl = endRedirectUrl

    "\\[(.*?)]".r.findAllIn(newEndRedirectUrl).foreach(m => {
      val parameter = HttpUtils.urlDecode(m.substring(1, m.length - 1)) // remove squared brackets and decode
      (originalStudyLinkUrlQueryParameters \ parameter).asOpt[String] match {
        case Some(value) =>
          newEndRedirectUrl = newEndRedirectUrl.replace(m, HttpUtils.urlEncode(value))
        case None =>
          newEndRedirectUrl = newEndRedirectUrl.replace(m, "undefined")
          logger.info(s".enhanceQueryStringInEndRedirectUrl: Could not find '$parameter' in original study link")
      }
    })
    newEndRedirectUrl
  }

  private def confirmationCodeCookie(confirmationCode: String): Cookie = Cookie(
    name = "JATOS_CONFIRMATION_CODE",
    value = confirmationCode,
    maxAge = Some(86400), // 1 day
    path = Common.getJatosUrlBasePath,
    httpOnly = false)

}
