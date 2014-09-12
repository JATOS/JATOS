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
 * @author madsen
 */
public class ExternalAssets extends Controller {

	private static final String APPLICATION_CONF = "/conf/application.conf";
	private static final String PROPERTY_STUDIESPATH = "mecharg.studiespath";
	private static final String CLASS_NAME = ExternalAssets.class
			.getSimpleName();
	private static final String DEFAULT_STUDIES_PATH = "/studies";

	private static String basePath = Play.application().path().getPath();
	private static String configPath = basePath + APPLICATION_CONF;
	private static Config config = ConfigFactory
			.parseFile(new File(configPath));

	/**
	 * If the "studiespath" is defined in mecharg.conf then use it
	 * as the base path. If the file can't be found there, try MechArg's base
	 * path + "studies" instead.
	 */
	public static Result at(String fileStr) {
		String studiesPath = null;
		try {
			studiesPath = config.getString(PROPERTY_STUDIESPATH);
		} catch (ConfigException e) {
			Logger.info(CLASS_NAME + ".at: Property " + PROPERTY_STUDIESPATH
					+ " in config file " + configPath + " doesn't exist.");
		}
		if (studiesPath != null) {
			studiesPath = studiesPath.replace("~",
					System.getProperty("user.home"));
			String fullPath = studiesPath + "/" + fileStr;
			File file = new File(fullPath);
			if (file.exists() && !file.isDirectory()) {
				Logger.info(CLASS_NAME + ".at: loading file " + fullPath + ".");
				return ok(file, true);
			}
		}
		Logger.info(CLASS_NAME + ".at: failed loading from path " + studiesPath
				+ "/" + fileStr + " specified " + "in config " + configPath
				+ ".");

		// Try in default location
		String fullPath = basePath + DEFAULT_STUDIES_PATH + "/" + fileStr;
		File file = new File(fullPath);
		if (file.exists() && !file.isDirectory()) {
			Logger.info(CLASS_NAME + ".at: loading file " + fullPath + ".");
			return ok(file, true);
		}

		Logger.info(CLASS_NAME + ".at: failed loading file from default path "
				+ fullPath + ".");
		return notFound(views.html.publix.error.render("Resource \"" + fileStr
				+ "\" couldn't be found."));
	}

}
