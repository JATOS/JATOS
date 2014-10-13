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
 * Manages access to files in the external studies directory (outside of
 * MechArg's packed Jar).
 * 
 * @author Kristian Lange
 */
public class ExternalAssets extends Controller {

	/**
	 * Part of the URL path that defines the location of the studies (isn't
	 * necessarily the same as DEFAULT_STUDIES_PATH)
	 */
	public static final String URL_STUDIES_PATH = "studies";

	private static final String CLASS_NAME = ExternalAssets.class
			.getSimpleName();

	/**
	 * Path to application.conf
	 */
	private static final String APPLICATION_CONF = "/conf/application.conf";

	/**
	 * Property name in application config for studies path
	 */
	private static final String PROPERTY_STUDIESPATH = "mecharg.studiespath";

	/**
	 * Default path to the studies directory in case it wasn't specified in the
	 * config
	 */
	private static final String DEFAULT_STUDIES_PATH = "/studies";
	private static final String BASEPATH = Play.application().path().getPath();
	private static final String CONFIGPATH = BASEPATH + APPLICATION_CONF;
	private static final Config CONFIG = ConfigFactory.parseFile(new File(
			CONFIGPATH));

	/**
	 * If the PROPERTY_STUDIESPATH is defined in the configuration file then use
	 * it as the base path. If PROPERTY_STUDIESPATH isn't defined, try in
	 * default study path instead.
	 */
	public static String STUDIESPATH;
	static {
		String rawConfigStudiesPath = CONFIG.getString(PROPERTY_STUDIESPATH);
		if (rawConfigStudiesPath != null && !rawConfigStudiesPath.isEmpty()) {
			STUDIESPATH = rawConfigStudiesPath.replace("~",
					System.getProperty("user.home"));
		} else {
			STUDIESPATH = BASEPATH + DEFAULT_STUDIES_PATH;
		}
		Logger.info(CLASS_NAME + ": Path to studies is " + STUDIESPATH);
	}

	public static Result at(Long studyId, String filePath) {
		File file;
		try {
			String studyPath = IOUtils.generateStudysPath(studyId);
			file = IOUtils.getExistingFileSecurely(studyPath, filePath);
			Logger.info(CLASS_NAME + ".at: loading file " + file.getPath()
					+ ".");
		} catch (IOException e) {
			Logger.info(CLASS_NAME + ".at: failed loading from path "
					+ STUDIESPATH + File.separator + filePath);
			return notFound(views.html.publix.error.render("Resource \""
					+ filePath + "\" couldn't be found."));
		}
		return ok(file, true);
	}

	public static String getComponentUrlPath(StudyModel study,
			ComponentModel component) {
		return "/" + URL_STUDIES_PATH + "/" + study.getId() + "/"
				+ component.getFilePath();
	}
}
