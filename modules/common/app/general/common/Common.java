package general.common;

import play.Configuration;
import play.Logger;
import play.Logger.ALogger;
import play.api.Application;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * This class provides configuration that is common to all modules of JATOS. It
 * mostly takes parameters from application.conf. It is initialized during JATOS
 * start (triggered in GuiceConfig). Since most fields are initialized by the
 * constructor during the JATOS' start (triggered in GuiceConfig), it's save to
 * access them via static getter methods.
 *
 * @author Kristian Lange
 */
@Singleton
public class Common {

    private static final ALogger LOGGER = Logger.of(Common.class);

    /**
     * JATOS version
     */
    public static final String JATOS_VERSION = Common.class.getPackage().getImplementationVersion();

    /**
     * Property name in application config for the path in the file system to
     * the study assets root directory, the directory where all study assets are
     * located
     */
    private static final String PROPERTY_STUDY_ASSETS_ROOT_PATH = "jatos.studyAssetsRootPath";
    public static final String PROPERTY_JATOS_STUDY_LOGS_PATH = "jatos.studyLogsPath";

    /**
     * JATOS' absolute base path without trailing '/.'
     */
    private static String basepath;

    /**
     * Path in the file system to the study assets root directory. If the
     * property is defined in the configuration file then use it as the base
     * path. If property isn't defined, try in default study path instead.
     */
    private static String studyAssetsRootPath;

    /**
     * Path in the file system where JATOS stores its logs for each study
     */
    private static String studyLogsPath;

    /**
     * Is true if an in-memory database is used.
     */
    private static boolean inMemoryDb;

    /**
     * Time in minutes when the Play session will timeout (defined in
     * application.conf)
     */
    private static int userSessionTimeout;

    /**
     * Time in minutes a user can be inactive before he will be logged-out
     * (defined in application.conf)
     */
    private static int userSessionInactivity;

    /**
     * Toggle for user session validation (not the Play session validation which is done by Play).
     */
    private static boolean userSessionValidation;

    /**
     * Database URL as defined in application.conf
     */
    private static String dbDefaultUrl;

    /**
     * Database driver as defined in application.conf
     */
    private static String dbDefaultDriver;

    /**
     * JPA persistence unit as defined in application.conf
     */
    private static String jpaDefault;

    /**
     * MAC address of the network interface
     */
    private static String mac;

    @Inject
    Common(Application application, Configuration configuration) {
        basepath = fillBasePath(application);
        studyAssetsRootPath = fillStudyAssetsRootPath(configuration);
        studyLogsPath = fillStudyLogsPath(configuration);
        inMemoryDb = configuration.getString("db.default.url").contains("jdbc:h2:mem:");
        userSessionTimeout = configuration.getInt("jatos.userSession.timeout");
        userSessionInactivity = configuration.getInt("jatos.userSession.inactivity");
        userSessionValidation = configuration.getBoolean("jatos.userSession.validation");
        if (!userSessionValidation) {
            LOGGER.warn("WARNING - User session validation is switched off. " +
                    "This decreases security. Proceed only if you know what you are doing.");
        }
        dbDefaultUrl = configuration.getString("db.default.url");
        dbDefaultDriver = configuration.getString("db.default.driver");
        jpaDefault = configuration.getString("jpa.default");
        mac = fillMac();
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
        String tempStudyAssetsRootPath = configuration.getString(PROPERTY_STUDY_ASSETS_ROOT_PATH);
        if (tempStudyAssetsRootPath == null || tempStudyAssetsRootPath.trim().isEmpty()) {
            LOGGER.error("Missing configuration of path to study assets directory: "
                            + "It must be set in application.conf under "
                            + PROPERTY_STUDY_ASSETS_ROOT_PATH + ".");
            System.exit(1);
        }

        // Replace ~ with actual home directory
        tempStudyAssetsRootPath =
                tempStudyAssetsRootPath.replace("~", System.getProperty("user.home"));
        // Replace Unix-like file separator with actual system's one
        tempStudyAssetsRootPath = tempStudyAssetsRootPath.replace("/", File.separator);
        // If relative path add JATOS' base path as prefix
        if (!(new File(tempStudyAssetsRootPath).isAbsolute())) {
            tempStudyAssetsRootPath = basepath + File.separator
                    + tempStudyAssetsRootPath;
        }
        LOGGER.info("Path to study assets directory is " + tempStudyAssetsRootPath);
        return tempStudyAssetsRootPath;
    }

    private String fillStudyLogsPath(Configuration configuration) {
        String tmpStudyLogPath = configuration.getString(PROPERTY_JATOS_STUDY_LOGS_PATH);
        if (tmpStudyLogPath == null || tmpStudyLogPath.trim().isEmpty()) {
            LOGGER.error("Missing configuration of path to study logs directory: "
                    + "It must be set in application.conf under "
                    + PROPERTY_JATOS_STUDY_LOGS_PATH + ".");
            System.exit(1);
        }

        // Replace ~ with actual home directory
        tmpStudyLogPath = tmpStudyLogPath.replace("~", System.getProperty("user.home"));
        // Replace Unix-like file separator with actual system's one
        tmpStudyLogPath = tmpStudyLogPath.replace("/", File.separator);
        // If relative path add JATOS' base path as prefix
        if (!(new File(tmpStudyLogPath).isAbsolute())) {
            tmpStudyLogPath = basepath + File.separator
                    + tmpStudyLogPath;
        }
        LOGGER.info("Path to study logs directory is " + tmpStudyLogPath);
        return tmpStudyLogPath;
    }

    private String fillMac() {
        String macStr = "unknown";
        try {
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            while (networks.hasMoreElements()) {
                NetworkInterface network = networks.nextElement();
                byte[] mac = network.getHardwareAddress();

                if (mac != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                    }
                    macStr = sb.toString();
                }
            }
        } catch (SocketException e) {
            LOGGER.info("Couldn't get network MAC address for study log");
        }
        return macStr;
    }

    public static String getJatosVersion() {
        return JATOS_VERSION;
    }

    public static String getBasepath() {
        return basepath;
    }

    public static String getStudyAssetsRootPath() {
        return studyAssetsRootPath;
    }

    public static String getStudyLogsPath() {
        return studyLogsPath;
    }

    public static boolean isInMemoryDb() {
        return inMemoryDb;
    }

    public static int getUserSessionTimeout() {
        return userSessionTimeout;
    }

    public static int getUserSessionInactivity() {
        return userSessionInactivity;
    }

    public static boolean getUserSessionValidation() {
        return userSessionValidation;
    }

    public static String getDbDefaultUrl() {
        return dbDefaultUrl;
    }

    public static String getDbDefaultDriver() {
        return dbDefaultDriver;
    }

    public static String getJpaDefault() {
        return jpaDefault;
    }

    public static String getMac() {
        return mac;
    }

}
