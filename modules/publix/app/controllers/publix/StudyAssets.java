package controllers.publix;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.NotFoundPublixException;
import exceptions.publix.PublixException;
import general.common.Common;
import general.common.MessagesStrings;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Controller;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import services.publix.idcookie.IdCookieService;
import utils.common.HttpUtils;
import utils.common.IOUtils;

/**
 * Manages web-access to files in the external study assets directories (outside
 * of JATOS' packed Jar).
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyAssets extends Controller {

	private static final ALogger LOGGER = Logger.of(StudyAssets.class);
	private static final String URL_PATH_SEPARATOR = "/";

	/**
	 * Identifying part of any URL that indicates an access to the study assets
	 * directories.
	 */
	public static final String URL_STUDY_ASSETS = "study_assets";

	private final IOUtils ioUtils;
	private final IdCookieService idCookieService;

	@Inject
	StudyAssets(IOUtils ioUtils, IdCookieService idCookieService) {
		this.ioUtils = ioUtils;
		this.idCookieService = idCookieService;
	}

	/**
	 * Action called while routing. Translates the given file path from the URL
	 * into a file path of the OS's file system and returns the file.
	 */
	public Result versioned(String urlPath) {
		File file;
		try {
			checkProperAssets(urlPath);
			String filePath = urlPath.replace(URL_PATH_SEPARATOR,
					File.separator);
			file = ioUtils.getExistingFileSecurely(
					Common.getStudyAssetsRootPath(), filePath);
			LOGGER.debug(".versioned: loading file " + file.getPath() + ".");
		} catch (PublixException e) {
			String errorMsg = e.getMessage();
			LOGGER.info(".versioned: " + errorMsg);
			if (HttpUtils.isAjax()) {
				return forbidden(errorMsg);
			} else {
				return forbidden(views.html.publix.error.render(errorMsg));
			}
		} catch (IOException e) {
			LOGGER.info(".versioned: failed loading from path "
					+ Common.getStudyAssetsRootPath() + File.separator
					+ urlPath);
			String errorMsg = "Resource \"" + urlPath + "\" couldn't be found.";
			if (HttpUtils.isAjax()) {
				return notFound(errorMsg);
			} else {
				return notFound(views.html.publix.error.render(errorMsg));
			}
		}
		return ok(file, true);
	}

	/**
	 * Throws a ForbiddenPublixException if this request is not allowed to
	 * access the study assets given in the URL path. It compares the study
	 * assets that are within the given filePath with all study assets that are
	 * stored in the JATOS ID cookies. If at least one of them has the study
	 * assets then the filePath is allowed. If not a ForbiddenPublixException is
	 * thrown.
	 * 
	 * Drawback: It can't compare with the ID cookie that actually belongs to
	 * this study run since it has no way of find out which it is (we have no
	 * study result ID). But since all ID cookie originate in the same browser
	 * one can assume this worker is allowed to access the study assets.
	 */
	private void checkProperAssets(String urlPath) throws PublixException {
		String[] filePathArray = urlPath.split(URL_PATH_SEPARATOR);
		if (filePathArray.length == 0) {
			throw new ForbiddenPublixException(PublixErrorMessages
					.studyAssetsNotAllowedOutsideRun(urlPath));
		}
		String studyAssets = filePathArray[0];
		if (!idCookieService.oneIdCookieHasThisStudyAssets(studyAssets)) {
			throw new ForbiddenPublixException(PublixErrorMessages
					.studyAssetsNotAllowedOutsideRun(urlPath));
		}
	}

	/**
	 * Retrieves the component's HTML file from the study assets
	 */
	public Result retrieveComponentHtmlFile(String studyDirName,
			String componentHtmlFilePath) throws NotFoundPublixException {
		File file;
		try {
			file = ioUtils.getFileInStudyAssetsDir(studyDirName,
					componentHtmlFilePath);
		} catch (IOException e) {
			throw new NotFoundPublixException(MessagesStrings
					.htmlFilePathNotExist(studyDirName, componentHtmlFilePath));
		}
		// Prevent browser from caching pages - this would be an
		// security issue and additionally confuse the study flow
		response().setHeader("Cache-control", "no-cache, no-store");
		return ok(file, true).as("text/html; charset=utf-8");
	}

}
