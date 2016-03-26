package controllers.publix;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.base.Strings;
import com.ning.http.client.providers.netty.response.NettyResponse;

import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.NotFoundPublixException;
import general.common.Common;
import general.common.MessagesStrings;
import models.common.Component;
import play.Logger;
import play.libs.F.Promise;
import play.libs.ws.WSClient;
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

	private final WSClient ws;
	private final IOUtils ioUtils;
	private final Common common;
	private final PublixErrorMessages errorMessages;

	@Inject
	StudyAssets(WSClient ws, IOUtils ioUtils, Common common,
			PublixErrorMessages errorMessages) {
		this.ws = ws;
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
	 * the study assets directory name of this study. It gets it either from the
	 * session or from a request's header.
	 */
	private void checkProperAssets(String filePath)
			throws ForbiddenPublixException {
		String properStudyAssets;
		if (session(Publix.STUDY_ASSETS) != null) {
			properStudyAssets = session(Publix.STUDY_ASSETS);
		} else if (request().hasHeader(Publix.STUDY_ASSETS)) {
			properStudyAssets = request().getHeader(Publix.STUDY_ASSETS);
		} else {
			throw new ForbiddenPublixException(
					errorMessages.studyAssetsNotAllowedOutsideRun(filePath));
		}

		if (!filePath.startsWith(properStudyAssets + File.separator)) {
			throw new ForbiddenPublixException(
					errorMessages.studyAssetsNotAllowedOutsideRun(filePath));
		}
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
	 * change. Additionally it adds a header with the study assets name which is
	 * stored in the session. The study assets name is needed by
	 * StudyAssets.versioned to verify whether this study run is allowed to
	 * access those study assets.
	 */
	public Promise<Result> forwardTo(String url) {
		return ws.url(url)
				.setHeader(Publix.STUDY_ASSETS, session(Publix.STUDY_ASSETS))
				.get().map(response -> {
					// Prevent browser from caching pages - this would be an
					// security issue and additionally confuse the study flow
					response().setHeader("Cache-control", "no-cache, no-store");
					// Play's WSResponse has problems with the UTF-8 encoding.
					// So we use the underlying NettyResponse.
					NettyResponse nettyResponse = (NettyResponse) response
							.getUnderlying();
					return ok(nettyResponse.getResponseBody("UTF-8"))
							.as("text/html; charset=utf-8");
				});
	}

}
