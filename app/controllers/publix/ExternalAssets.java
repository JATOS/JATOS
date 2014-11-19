package controllers.publix;

import java.io.File;
import java.io.IOException;

import models.ComponentModel;
import models.StudyModel;
import play.Logger;
import play.Play;
import play.mvc.Controller;
import play.mvc.Result;
import services.IOUtils;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Manages web-access to files in the external studies directory (outside of
 * MechArg's packed Jar).
 * 
 * @author Kristian Lange
 */
public class ExternalAssets extends Controller {

	/**
	 * Part of the URL path that defines the location of the studies
	 */
	public static final String URL_STUDIES_PATH = "studies";

	private static final String CLASS_NAME = ExternalAssets.class
			.getSimpleName();

	/**
	 * Path to application.conf
	 */
	private static final String APPLICATION_CONF = "/conf/application.conf";

	/**
	 * Property name in application config for the path to the directory where
	 * all studies are located
	 */
	private static final String PROPERTY_STUDIES_ROOT_PATH = "mecharg.studiesRootPath";

	/**
	 * Default path to the studies directory in case it wasn't specified in the
	 * config
	 */
	private static final String DEFAULT_STUDIES_ROOT_PATH = "/studies";
	private static final String BASEPATH = Play.application().path().getPath();
	private static final String CONFIGPATH = BASEPATH + APPLICATION_CONF;
	private static final Config CONFIG = ConfigFactory.parseFile(new File(
			CONFIGPATH));

	/**
	 * If the PROPERTY_STUDIES_ROOT_PATH is defined in the configuration file
	 * then use it as the base path. If PROPERTY_STUDIES_ROOT_PATH isn't
	 * defined, try in default study path instead.
	 */
	public static String STUDIES_ROOT_PATH;
	static {
		String rawConfigStudiesPath = CONFIG
				.getString(PROPERTY_STUDIES_ROOT_PATH);
		if (rawConfigStudiesPath != null && !rawConfigStudiesPath.isEmpty()) {
			STUDIES_ROOT_PATH = rawConfigStudiesPath.replace("~",
					System.getProperty("user.home"));
		} else {
			STUDIES_ROOT_PATH = BASEPATH + DEFAULT_STUDIES_ROOT_PATH;
		}
		Logger.info(CLASS_NAME + ": Path to studies is " + STUDIES_ROOT_PATH);
	}

	/**
	 * Called while routing. Translates the given file path from the URL into a
	 * file path of the OS's file system and returns the file.
	 */
	public static Result at(String filePath) {
		File file;
		try {
			file = IOUtils.getExistingFileSecurely(STUDIES_ROOT_PATH, filePath);
			Logger.info(CLASS_NAME + ".at: loading file " + file.getPath()
					+ ".");
		} catch (IOException e) {
			Logger.info(CLASS_NAME + ".at: failed loading from path "
					+ STUDIES_ROOT_PATH + File.separator + filePath);
			return notFound(views.html.publix.error.render("Resource \""
					+ filePath + "\" couldn't be found."));
		}
		return ok(file, true);
	}

	public static String getComponentUrlPath(StudyModel study,
			ComponentModel component) {
		return "/" + URL_STUDIES_PATH + "/" + study.getDirName() + "/"
				+ component.getHtmlFilePath();
	}

	/**
	 * Generates an URL with protocol HTTP, request's hostname, given urlPath,
	 * and requests query string.
	 */
	public static String getUrlWithRequestQueryString(String urlPath) {
		String requestUrlPath = Publix.request().uri();
		int queryBegin = requestUrlPath.lastIndexOf("?");
		if (queryBegin > 0) {
			String queryString = requestUrlPath.substring(queryBegin + 1);
			urlPath = urlPath + "?" + queryString;
		}
		return getUrl(urlPath);
	}

	public static String getUrl(String urlPath) {
		String requestHostName = Publix.request().host(); // includes port
		return "http://" + requestHostName + urlPath;
	}
}
