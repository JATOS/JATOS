package general.common;

import com.google.common.base.Strings;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueType;
import org.apache.commons.lang3.tuple.Pair;
import play.Logger;
import play.Logger.ALogger;
import play.api.Application;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;

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
     * Property name in application config - path (file system) of study assets root directory
     * (directory where all study assets are located)
     */
    private static final String PROPERTY_STUDY_ASSETS_ROOT_PATH = "jatos.studyAssetsRootPath";

    /**
     * Property name in application config - path (file system) to study logs
     */
    private static final String PROPERTY_JATOS_STUDY_LOGS_PATH = "jatos.studyLogs.path";

    /**
     * Property name in application config - path (file system) to result upload files
     */
    private static final String PROPERTY_JATOS_RESULT_UPLOADS_PATH = "jatos.resultUploads.path";

    private static String jatosVersion;
    private static String basepath;
    private static String studyAssetsRootPath;
    private static boolean studyLogsEnabled;
    private static String studyLogsPath;
    private static boolean resultUploadsEnabled;
    private static String resultUploadsPath;
    private static long resultUploadsMaxFileSize;
    private static long resultUploadsLimitPerStudyRun;
    private static long resultDataMaxSize;
    private static int maxResultsDbQuerySize;
    private static boolean inMemoryDb;
    private static int userSessionTimeout;
    private static int userSessionInactivity;
    private static boolean userSessionValidation;
    private static String dbDefaultUrl;
    private static String dbDefaultDriver;
    private static String jpaDefault;
    private static String mac;
    private static int userPasswordLength;
    private static int userPasswordStrength;
    private static String playHttpContext;
    private static String jatosUpdateMsg;
    private static String jatosHttpAddress;
    private static int jatosHttpPort;
    private static String ldapUrl;
    private static List<String> ldapBaseDn;
    private static String ldapAdminDn;
    private static String ldapAdminPassword;
    private static int ldapTimeout;
    private static String oauthGoogleClientId;
    private static boolean donationAllowed;
    private static String termsOfUseUrl;
    private static String brandingUrl;
    private static boolean studyMembersAllowedToAddAllUsers;
    private static boolean idCookiesSecure;
    private static Http.Cookie.SameSite idCookiesSameSite;
    private static boolean resultDataExportUseTmpFile;

    /**
     * List of regular expressions and their description as Pairs that define password restrictions
     * (the regexes are from https://stackoverflow.com/questions/19605150)
     */
    private static final List<Pair<String, String>> userPasswordStrengthRegexList = Arrays.asList(
            Pair.of("No restrictions on characters.", "^.*$"),
            Pair.of("At least one Latin letter and one number.",
                    "^(?=.*?[A-Za-z])(?=.*?[0-9]).{2,}$"),
            Pair.of("At least one Latin letter, one number and one special character (#?!@$%^&*-).",
                    "^(?=.*?[A-Za-z])(?=.*?[0-9])(?=.*?[#?!@$%^&*-]).{3,}$"),
            Pair.of("At least one uppercase Latin letter, one lowercase Latin letter, one number and one special "
                            + "character (#?!@$%^&*-).",
                    "^(?=.*?[a-z])(?=.*?[A-Z])(?=.*?[0-9])(?=.*?[#?!@$%^&*-]).{4,}"));

    @Inject
    Common(Application application, Config config) {
        jatosVersion = BuildInfo.version();
        basepath = fillBasePath(application);
        studyAssetsRootPath = fillStudyAssetsRootPath(config);
        studyLogsEnabled = config.getBoolean("jatos.studyLogs.enabled");
        studyLogsPath = fillStudyLogsPath(config);
        resultUploadsEnabled = config.getBoolean("jatos.resultUploads.enabled");
        resultUploadsPath = fillResultUploadsPath(config);
        resultUploadsMaxFileSize = config.getBytes("jatos.resultUploads.maxFileSize");
        resultUploadsLimitPerStudyRun = config.getBytes("jatos.resultUploads.limitPerStudyRun");
        resultDataMaxSize = config.getBytes("jatos.resultData.maxSize");
        maxResultsDbQuerySize = config.getInt("jatos.maxResultsDbQuerySize");
        inMemoryDb = config.getString("db.default.url").contains("jdbc:h2:mem:");
        userSessionTimeout = config.getInt("jatos.userSession.timeout");
        userSessionInactivity = config.getInt("jatos.userSession.inactivity");
        userSessionValidation = config.getBoolean("jatos.userSession.validation");
        if (!userSessionValidation) {
            LOGGER.warn("User session validation is switched off. This decreases security.");
        }
        dbDefaultUrl = config.getString("db.default.url");
        dbDefaultDriver = config.getString("db.default.driver");
        jpaDefault = config.getString("jpa.default");
        mac = fillMac();
        userPasswordLength = config.getInt("jatos.user.password.length");
        userPasswordStrength = config.getInt("jatos.user.password.strength");
        if (userPasswordStrength > userPasswordStrengthRegexList.size()) {
            userPasswordStrength = 0;
        }
        playHttpContext = config.getString("play.http.context");
        jatosUpdateMsg = !config.getIsNull("jatos.update.msg") ? config.getString("jatos.update.msg") : null;
        jatosHttpAddress = config.getString("play.server.http.address");
        if (jatosHttpAddress.equals("0.0.0.0")) jatosHttpAddress = "127.0.0.1"; // Fix localhost IP
        jatosHttpPort = config.getInt("play.server.http.port");
        ldapUrl = config.getString("jatos.user.authentication.ldap.url");
        if (config.getValue("jatos.user.authentication.ldap.basedn").valueType() == ConfigValueType.STRING) {
            ldapBaseDn = Collections.singletonList(config.getString("jatos.user.authentication.ldap.basedn"));
        } else if (config.getValue("jatos.user.authentication.ldap.basedn").valueType() == ConfigValueType.LIST) {
            ldapBaseDn = config.getStringList("jatos.user.authentication.ldap.basedn");
        }
        ldapAdminDn = config.getString("jatos.user.authentication.ldap.admin.dn");
        ldapAdminPassword = config.getString("jatos.user.authentication.ldap.admin.password");
        ldapTimeout = config.getInt("jatos.user.authentication.ldap.timeout");
        oauthGoogleClientId = config.getString("jatos.user.authentication.oauth.googleClientId");
        donationAllowed = config.getBoolean("jatos.donationAllowed");
        termsOfUseUrl = config.getString("jatos.termsOfUseUrl");
        brandingUrl = config.getString("jatos.brandingUrl");
        studyMembersAllowedToAddAllUsers = config.getBoolean("jatos.studyMembers.allowAddAllUsers");
        idCookiesSecure = config.getBoolean("jatos.idCookies.secure");
        idCookiesSameSite = fillIdCookiesSameSite(config);
        resultDataExportUseTmpFile = config.getBoolean("jatos.resultData.export.useTmpFile");
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

    private String fillStudyAssetsRootPath(Config config) {
        String tempStudyAssetsRootPath = obtainPath(config, PROPERTY_STUDY_ASSETS_ROOT_PATH).orElseThrow(() ->
                new RuntimeException("Missing configuration of path to study assets directory: "
                        + "It must be set in application.conf under " + PROPERTY_STUDY_ASSETS_ROOT_PATH + "."));
        LOGGER.info("Path to study assets directory is " + tempStudyAssetsRootPath);
        return tempStudyAssetsRootPath;
    }

    private String fillStudyLogsPath(Config config) {
        String tmpStudyLogPath = obtainPath(config, PROPERTY_JATOS_STUDY_LOGS_PATH).orElseThrow(() ->
                new RuntimeException("Missing configuration of path to study logs directory: "
                        + "It must be set in application.conf under " + PROPERTY_JATOS_STUDY_LOGS_PATH + "."));
        LOGGER.info("Path to study logs directory is " + tmpStudyLogPath);
        return tmpStudyLogPath;
    }

    private String fillResultUploadsPath(Config config) {
        String tmpResultUploadsPath = obtainPath(config, PROPERTY_JATOS_RESULT_UPLOADS_PATH).orElseThrow(() ->
                new RuntimeException("Missing configuration of path to uploads directory: "
                        + "It must be set in application.conf under " + PROPERTY_JATOS_RESULT_UPLOADS_PATH + "."));
        LOGGER.info("Path to uploads directory is " + tmpResultUploadsPath);
        return tmpResultUploadsPath;
    }

    private Optional<String> obtainPath(Config config, String property) {
        String path = config.getString(property);
        if (Strings.isNullOrEmpty(path)) return Optional.empty();

        // Replace ~ with actual home directory
        path = path.replace("~", System.getProperty("user.home"));
        // Replace Unix-like file separator with actual system's one
        path = path.replace("/", File.separator);
        // If relative path add JATOS' base path as prefix
        if (!(new File(path).isAbsolute())) {
            path = basepath + File.separator + path;
        }
        return Optional.of(path);
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

    private Http.Cookie.SameSite fillIdCookiesSameSite(Config config) {
        String idCookiesSameSiteRaw = config.getString("jatos.idCookies.sameSite");
        if (idCookiesSameSiteRaw.equalsIgnoreCase("lax")) {
            return Http.Cookie.SameSite.LAX;
        } else if (idCookiesSameSiteRaw.equalsIgnoreCase("strict")) {
            return Http.Cookie.SameSite.STRICT;
        } else {
            return null; // Play doesn't support SameSite.None yet
        }
    }

    /**
     * JATOS version (full version e.g. v3.5.5-alpha)
     */
    public static String getJatosVersion() {
        return jatosVersion;
    }

    /**
     * JATOS' absolute base path without trailing '/.'
     */
    public static String getBasepath() {
        return basepath;
    }

    /**
     * Path in the file system to the study assets root directory. If the
     * property is defined in the configuration file then use it as the base
     * path. If property isn't defined, try in default study path instead.
     */
    public static String getStudyAssetsRootPath() {
        return studyAssetsRootPath;
    }

    /**
     * Is study logging enabled
     */
    public static boolean isStudyLogsEnabled() {
        return studyLogsEnabled;
    }

    /**
     * Path in the file system where JATOS stores its logs for each study
     */
    public static String getStudyLogsPath() {
        return studyLogsPath;
    }

    /**
     * Are file uploads via jatos.js allowed?
     */
    public static boolean isResultUploadsEnabled() {
        return resultUploadsEnabled;
    }

    /**
     * Path in the file system where JATOS stores uploaded result files
     */
    public static String getResultUploadsPath() {
        return resultUploadsPath;
    }

    /**
     * Max file size in bytes for a single uploaded file
     */
    public static long getResultUploadsMaxFileSize() {
        return resultUploadsMaxFileSize;
    }

    /**
     * Max size of all files uploaded during a single study run in bytes
     */
    public static long getResultUploadsLimitPerStudyRun() {
        return resultUploadsLimitPerStudyRun;
    }

    /**
     * Maximal size of result data of one component result in Byte
     */
    public static long getResultDataMaxSize() {
        return resultDataMaxSize;
    }

    /**
     * Maximal number of results to be fetched from the DB at once
     */
    public static int getMaxResultsDbQuerySize() {
        return maxResultsDbQuerySize;
    }

    /**
     * Is true if an in-memory database is used.
     */
    public static boolean isInMemoryDb() {
        return inMemoryDb;
    }

    /**
     * Time in minutes when the Play session will timeout (defined in
     * application.conf)
     */
    public static int getUserSessionTimeout() {
        return userSessionTimeout;
    }

    /**
     * Time in minutes a user can be inactive before he will be logged-out
     * (defined in application.conf)
     */
    public static int getUserSessionInactivity() {
        return userSessionInactivity;
    }

    /**
     * Toggle for user session validation (not the Play session validation which is done by Play).
     */
    public static boolean isUserSessionValidation() {
        return userSessionValidation;
    }

    /**
     * Database URL as defined in application.conf
     */
    public static String getDbDefaultUrl() {
        return dbDefaultUrl;
    }

    /**
     * Database driver as defined in application.conf
     */
    public static String getDbDefaultDriver() {
        return dbDefaultDriver;
    }

    /**
     * JPA persistence unit as defined in application.conf
     */
    public static String getJpaDefault() {
        return jpaDefault;
    }

    /**
     * MAC address of the network interface
     */
    public static String getMac() {
        return mac;
    }

    /**
     * Message that will be displayed during user creation that describes password requirements
     */
    public static int getUserPasswordMinLength() {
        return userPasswordLength;
    }

    /**
     * Regex that will be used to check the password during user creation
     */
    public static Pair<String, String> getUserPasswordStrengthRegex() {
        return userPasswordStrengthRegexList.get(userPasswordStrength);
    }

    /**
     * HTTP URL base path: will be the prefix for each URL, e.g. /jatos/test -> /myBasePath/jatos/test
     */
    public static String getPlayHttpContext() {
        return playHttpContext;
    }

    /**
     * If in update happened during last startup a message might be stored here
     */
    public static String getJatosUpdateMsg() {
        return jatosUpdateMsg;
    }

    /**
     * JATOS HTTP host address without protocol or port (e.g. 192.168.0.1)
     */
    public static String getJatosHttpAddress() {
        return jatosHttpAddress;
    }

    /**
     * Port JATOS is running on
     */
    public static int getJatosHttpPort() {
        return jatosHttpPort;
    }

    /**
     * LDAP URL (with port)
     */
    public static String getLdapUrl() {
        return ldapUrl;
    }

    /**
     * LDAP base DNs (Distinguished Name)
     */
    public static List<String> getLdapBaseDn() {
        return ldapBaseDn;
    }

    /**
     * LDAP admin DN (Distinguished Name) - the admin user is used to search for the actual user that wants to log in
     */
    public static String getLdapAdminDn() {
        return ldapAdminDn;
    }

    /**
     * LDAP admin password
     */
    public static String getLdapAdminPassword() {
        return ldapAdminPassword;
    }

    public static boolean isLdapAllowed() {
        return !Strings.isNullOrEmpty(ldapUrl);
    }

    public static boolean isLdapAutoCreate() {
        return true;
    }

    /**
     * Read timeout for the LDAP server
     */
    public static int getLdapTimeout() {
        return ldapTimeout;
    }

    public static boolean isOauthGoogleAllowed() {
        return !Strings.isNullOrEmpty(oauthGoogleClientId);
    }

    /**
     * Google Sign-in Client ID for OAuth / OpenId Connect (OIDC)
     */
    public static String getOauthGoogleClientId() {
        return oauthGoogleClientId;
    }

    /**
     * Should the GUI show a donations button
     */
    public static boolean isDonationAllowed() {
        return donationAllowed;
    }

    /**
     * URL to the terms of use that will be shown in a link on the home page
     */
    public static String termsOfUseUrl() {
        return termsOfUseUrl;
    }

    /**
     * URL where some static HTML can be found that can be shown instead of the default welcome message on the home page
     */
    public static String getBrandingUrl() {
        return brandingUrl;
    }

    /**
     * If true, it's allowed to add all users that exist on this JATOS server to be added at once as members of a study
     */
    public static boolean isStudyMembersAllowedToAddAllUsers() {
        return studyMembersAllowedToAddAllUsers;
    }

    /**
     * If true, the ID cookies' secure attribute will be set
     */
    public static boolean isIdCookiesSecure() {
        return idCookiesSecure;
    }

    /**
     * Which SameSite attribute the ID cookies should set
     */
    public static Http.Cookie.SameSite getIdCookiesSameSite() {
        return idCookiesSameSite;
    }

    /**
     * If true, result data that are fetched from the database are first stored in a temporary file and only if
     * they are all gathered the file is sent to the browser. If false the result data are streamed directly from the
     * database to the browser.
     */
    public static boolean isResultDataExportUseTmpFile() {
        return resultDataExportUseTmpFile;
    }
}
