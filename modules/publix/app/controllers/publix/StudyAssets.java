package controllers.publix;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.NotFoundPublixException;
import general.common.Common;
import general.common.MessagesStrings;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import services.publix.PublixErrorMessages;
import utils.common.ControllerUtils;
import utils.common.IOUtils;

/**
 * Manages web-access to files in the external study assets directories (outside
 * of JATOS' packed Jar).
 * 
 * @author Kristian Lange
 */
@Singleton
public class StudyAssets extends Controller {

	private static final String CLASS_NAME = StudyAssets.class.getSimpleName();

	/**
	 * Identifying part of any URL that indicates an access to the study assets
	 * directories.
	 */
	public static final String URL_STUDY_ASSETS = "study_assets";

	private final IOUtils ioUtils;
	private final Common common;
	private final PublixErrorMessages errorMessages;

	@Inject
	StudyAssets(IOUtils ioUtils, Common common,
			PublixErrorMessages errorMessages) {
		this.ioUtils = ioUtils;
		this.common = common;
		this.errorMessages = errorMessages;
	}

	/**
	 * Action called while routing. Translates the given file path from the URL
	 * into a file path of the OS's file system and returns the file.
	 */
	public Result versioned(String filePath) {
		File file;
		try {
			filePath = filePath.replace("/", File.separator);
			checkProperAssets(filePath);
			file = ioUtils.getExistingFileSecurely(
					common.getStudyAssetsRootPath(), filePath);
			Logger.info(CLASS_NAME + ".versioned: loading file "
					+ file.getPath() + ".");
		} catch (ForbiddenPublixException e) {
			String errorMsg = e.getMessage();
			Logger.info(CLASS_NAME + ".versioned: " + errorMsg);
			if (ControllerUtils.isAjax()) {
				return forbidden(errorMsg);
			} else {
				return forbidden(views.html.publix.error.render(errorMsg));
			}
		} catch (IOException e) {
			Logger.info(CLASS_NAME + ".versioned: failed loading from path "
					+ common.getStudyAssetsRootPath() + File.separator
					+ filePath);
			String errorMsg = "Resource \"" + filePath
					+ "\" couldn't be found.";
			if (ControllerUtils.isAjax()) {
				return notFound(errorMsg);
			} else {
				return notFound(views.html.publix.error.render(errorMsg));
			}
		}
		return ok(file, true);
	}

	/**
	 * Throws a ForbiddenPublixException if this request is not allowed to
	 * access the study assets given in the filePath. For comparison it needs
	 * the study assets directory name of this study and it gets it from the
	 * session.
	 */
	private void checkProperAssets(String filePath)
			throws ForbiddenPublixException {
		String properStudyAssets;
		if (session(Publix.STUDY_ASSETS) != null) {
			properStudyAssets = session(Publix.STUDY_ASSETS);
		} else {
			throw new ForbiddenPublixException(
					errorMessages.studyAssetsNotAllowedOutsideRun(filePath));
		}

		if (!filePath.startsWith(properStudyAssets + File.separator)) {
			throw new ForbiddenPublixException(
					errorMessages.studyAssetsNotAllowedOutsideRun(filePath));
		}
	}

	/**
	 * Retrieves the component's HTML file from the study assets
	 */
	public Result retrieveComponentHtmlFile(String studyDirName,
			String componentHtmlFilePath) throws NotFoundPublixException {
		File file = null;
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
