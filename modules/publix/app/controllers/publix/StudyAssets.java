package controllers.publix;

import java.io.File;
import java.io.IOException;

import javax.inject.Singleton;

import models.common.Component;
import play.Logger;
import play.libs.F.Promise;
import play.libs.ws.WS;
import play.mvc.Controller;
import play.mvc.Result;
import utils.common.ControllerUtils;
import utils.common.IOUtils;

import com.google.common.base.Strings;

import exceptions.publix.NotFoundPublixException;
import general.common.Common;
import general.common.MessagesStrings;

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

	/**
	 * Action called while routing. Translates the given file path from the URL
	 * into a file path of the OS's file system and returns the file.
	 */
	public Result at(String filePath) {
		File file;
		try {
			filePath = filePath.replace("/", File.separator);
			file = IOUtils.getExistingFileSecurely(
					Common.STUDY_ASSETS_ROOT_PATH, filePath);
			Logger.info(CLASS_NAME + ".at: loading file " + file.getPath()
					+ ".");
		} catch (IOException e) {
			Logger.info(CLASS_NAME + ".at: failed loading from path "
					+ Common.STUDY_ASSETS_ROOT_PATH + File.separator + filePath);
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

	public static String getComponentUrlPath(String studyAssetsDirName,
			Component component) throws NotFoundPublixException {
		if (Strings.isNullOrEmpty(studyAssetsDirName)
				|| Strings.isNullOrEmpty(component.getHtmlFilePath())) {
			throw new NotFoundPublixException(
					MessagesStrings.htmlFilePathEmpty(component.getId()));
		}
		return "/" + URL_STUDY_ASSETS + "/" + studyAssetsDirName + "/"
				+ component.getHtmlFilePath();
	}

	/**
	 * Generates an URL with protocol HTTP. Takes the hostname from the request,
	 * the url's path from the given urlPath, and the query string again from
	 * the request.
	 */
	public static String getUrlWithQueryString(String oldUri,
			String requestHost, String newUrlPath) {
		// Check if we have an query string (begins with '?')
		int queryBegin = oldUri.lastIndexOf("?");
		if (queryBegin > 0) {
			String queryString = oldUri.substring(queryBegin + 1);
			newUrlPath = newUrlPath + "?" + queryString;
		}

		// It would be nice if Play has a way to find out which protocol it
		// uses. Apparently it changes http automatically into https if it uses
		// encryption (at least when I checked with Play 2.2.3).
		return "http://" + requestHost + newUrlPath;
	}

	/**
	 * Like an internal redirect or an proxy. The URL in the browser doesn't
	 * change.
	 */
	public Promise<Result> forwardTo(String url) {
		return WS.url(url).get().map(response -> {
			// Prevent browser from caching pages - this would be an
			// security issue and additionally confuse the study flow
				response().setHeader("Cache-control", "no-cache, no-store");
				return ok(response.getBody()).as("text/html; charset=utf-8");
			});
	}

}
