package general.common;

import java.io.File;

import play.Logger;
import play.Play;

public class Common {

	private static final String CLASS_NAME = Common.class.getSimpleName();
	
	/**
	 * JATOS' absolute base path without trailing '/.'
	 */
	public static final String BASEPATH = getBasePath();

	private static String getBasePath() {
		String tempBasePath = Play.application().path().getAbsolutePath();
		if (tempBasePath.endsWith(File.separator + ".")) {
			tempBasePath = tempBasePath.substring(0, tempBasePath.length() - 2);
		}
		if (tempBasePath.endsWith(File.separator)) {
			tempBasePath = tempBasePath.substring(0, tempBasePath.length() - 1);
		}
		return tempBasePath;
	}
	
	/**
	 * Property name in application config for the path in the file system to
	 * the study assets root directory, the directory where all study assets are
	 * located
	 */
	private static final String PROPERTY_STUDY_ASSETS_ROOT_PATH = "jatos.studyAssetsRootPath";

	/**
	 * Default path in the file system to the study assets root directory in
	 * case it wasn't specified in the config
	 */
	private static final String DEFAULT_STUDY_ASSETS_ROOT_PATH = "study_assets_root";

	/**
	 * Path in the file system to the study assets root directory. If the
	 * property is defined in the configuration file then use it as the base
	 * path. If property isn't defined, try in default study path instead.
	 */
	public static final String STUDY_ASSETS_ROOT_PATH = getStudyAssetsRootPath();

	private static String getStudyAssetsRootPath() {
		String tempStudyAssetsRootPath = Play.application().configuration()
				.getString(PROPERTY_STUDY_ASSETS_ROOT_PATH);
		if (tempStudyAssetsRootPath != null
				&& !tempStudyAssetsRootPath.trim().isEmpty()) {
			// Replace ~ with actual home directory
			tempStudyAssetsRootPath = tempStudyAssetsRootPath.replace("~",
					System.getProperty("user.home"));
			// Replace Unix-like file separator with actual system's one
			tempStudyAssetsRootPath = tempStudyAssetsRootPath.replace("/",
					File.separator);
		} else {
			tempStudyAssetsRootPath = DEFAULT_STUDY_ASSETS_ROOT_PATH;
		}

		// If relative path add JATOS' base path as prefix
		if (!tempStudyAssetsRootPath.startsWith(File.separator)) {
			tempStudyAssetsRootPath = Common.BASEPATH + File.separator
					+ tempStudyAssetsRootPath;
		}
		Logger.info(CLASS_NAME + ": Path to study assets directory is "
				+ tempStudyAssetsRootPath);
		return tempStudyAssetsRootPath;
	}
	
	/**
	 * JATOS version
	 */
	public static final String VERSION = Common.class.getPackage()
			.getImplementationVersion();

	/**
	 * Is true if an in-memory database is used.
	 */
	public static final boolean IN_MEMORY_DB = Play.application()
			.configuration().getString("db.default.url")
			.contains("jdbc:h2:mem:");

}
