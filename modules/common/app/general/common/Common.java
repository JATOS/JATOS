package general.common;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

import play.Configuration;
import play.Logger;
import play.Logger.ALogger;
import play.api.Application;

/**
 * This class provides configuration that is common to all modules of JATOS. It
 * is initialized during JATOS start (triggered in GuiceConfig).
 * 
 * @author Kristian Lange
 */
@Singleton
public class Common {

	private static final ALogger LOGGER = Logger.of(Common.class);

	/**
	 * JATOS version
	 */
	public static final String VERSION = Common.class.getPackage()
			.getImplementationVersion();

	/**
	 * Property name in application config for the path in the file system to
	 * the study assets root directory, the directory where all study assets are
	 * located
	 */
	private static final String PROPERTY_STUDY_ASSETS_ROOT_PATH = "jatos.studyAssetsRootPath";

	/**
	 * JATOS' absolute base path without trailing '/.'
	 */
	private final String basepath;

	/**
	 * Path in the file system to the study assets root directory. If the
	 * property is defined in the configuration file then use it as the base
	 * path. If property isn't defined, try in default study path instead.
	 */
	private final String studyAssetsRootPath;

	/**
	 * Is true if an in-memory database is used.
	 */
	private final boolean inMemoryDb;

	public static int SESSION_TIMEOUT;
	public static int SESSION_INACTIVITY;

	@Inject
	Common(Application application, Configuration configuration) {
		this.basepath = fillBasePath(application);
		this.studyAssetsRootPath = fillStudyAssetsRootPath(configuration);
		this.inMemoryDb = configuration.getString("db.default.url")
				.contains("jdbc:h2:mem:");
		SESSION_TIMEOUT = configuration.getInt("jatos.session.timeout");
		SESSION_INACTIVITY = configuration.getInt("jatos.session.inactivity");
	}

	private String fillBasePath(Application application) {
		String tempBasePath = application.path().getAbsolutePath();
		if (tempBasePath.endsWith(File.separator + ".")) {
			tempBasePath = tempBasePath.substring(0, tempBasePath.length() - 2);
		}
		if (tempBasePath.endsWith(File.separator)) {
			tempBasePath = tempBasePath.substring(0, tempBasePath.length() - 1);
		}
		return tempBasePath;
	}

	private String fillStudyAssetsRootPath(Configuration configuration) {
		String tempStudyAssetsRootPath = configuration
				.getString(PROPERTY_STUDY_ASSETS_ROOT_PATH);
		if (tempStudyAssetsRootPath == null
				|| tempStudyAssetsRootPath.trim().isEmpty()) {
			LOGGER.error(
					"Missing configuration of path to study assets directory: "
							+ "It must be set in application.conf under "
							+ PROPERTY_STUDY_ASSETS_ROOT_PATH + ".");
			System.exit(1);
		}

		// Replace ~ with actual home directory
		tempStudyAssetsRootPath = tempStudyAssetsRootPath.replace("~",
				System.getProperty("user.home"));
		// Replace Unix-like file separator with actual system's one
		tempStudyAssetsRootPath = tempStudyAssetsRootPath.replace("/",
				File.separator);

		// If relative path add JATOS' base path as prefix

		if (!(new File(tempStudyAssetsRootPath).isAbsolute())) {
			tempStudyAssetsRootPath = this.basepath + File.separator
					+ tempStudyAssetsRootPath;
		}
		LOGGER.info(
				"Path to study assets directory is " + tempStudyAssetsRootPath);
		return tempStudyAssetsRootPath;
	}

	public String getBasepath() {
		return basepath;
	}

	public String getStudyAssetsRootPath() {
		return studyAssetsRootPath;
	}

	public boolean isInMemoryDb() {
		return inMemoryDb;
	}

}
