package controllers.publix;

import java.io.File;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

import play.Logger;
import play.Play;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * Manages access to external files that are outside of MechArg's packed Jar.
 * 
 * @author Kristian Lange
 */
public class ExternalAssets extends Controller {

	private static final String APPLICATION_CONF = "/conf/application.conf";
	private static final String PROPERTY_STUDIESPATH = "mecharg.studiespath";
	private static final String CLASS_NAME = ExternalAssets.class
			.getSimpleName();
	private static final String DEFAULT_STUDIES_PATH = "/studies";
	private static final String BASEPATH = Play.application().path().getPath();
	private static final String CONFIGPATH = BASEPATH + APPLICATION_CONF;
	private static final Config CONFIG = ConfigFactory.parseFile(new File(
			CONFIGPATH));

	private static String STUDIESPATH;
	static {
		try {
			String rawStudiesPath = CONFIG.getString(PROPERTY_STUDIESPATH);
			STUDIESPATH = rawStudiesPath.replace("~",
					System.getProperty("user.home"));
			Logger.info(CLASS_NAME + ": Path to studies is " + STUDIESPATH);
		} catch (ConfigException e) {
			Logger.info(CLASS_NAME + ": Path to studies in property "
					+ PROPERTY_STUDIESPATH + " in config file " + CONFIGPATH
					+ " doesn't exist.");
		}
	}

	/**
	 * If the PROPERTY_STUDIESPATH is defined in the configuration file then use
	 * it as the base path. If PROPERTY_STUDIESPATH isn't defined, try in
	 * default study path instead.
	 */
	public static Result at(String fileStr) {
		if (STUDIESPATH != null) {
			// Try in location in PROPERTY_STUDIESPATH from config file
			String fullPath = STUDIESPATH + "/" + fileStr;
			File file = new File(fullPath);
			if (file.exists() && !file.isDirectory()) {
				Logger.info(CLASS_NAME + ".at: loading file " + fullPath + ".");
				return ok(file, true);
			} else {
				Logger.info(CLASS_NAME + ".at: failed loading from path "
						+ STUDIESPATH + "/" + fileStr + " specified "
						+ "in config " + CONFIGPATH + ".");
			}
		} else {
			// Try in default location
			String fullPath = BASEPATH + DEFAULT_STUDIES_PATH + "/" + fileStr;
			File file = new File(fullPath);
			if (file.exists() && !file.isDirectory()) {
				Logger.info(CLASS_NAME + ".at: loading file " + fullPath + ".");
				return ok(file, true);
			} else {
				Logger.info(CLASS_NAME
						+ ".at: failed loading file from default path "
						+ fullPath + ".");
			}
		}
		return notFound(views.html.publix.error.render("Resource \"" + fileStr
				+ "\" couldn't be found."));
	}

}
