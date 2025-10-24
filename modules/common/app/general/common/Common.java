package general.common;

import com.google.common.base.Strings;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueType;
import org.apache.commons.lang3.tuple.Pair;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * This class provides configuration properties that are common to all modules of JATOS. It mostly takes parameters from
 * application.conf. It is initialized during JATOS start (triggered in GuiceConfig). Since most fields are initialized
 * by the constructor during the JATOS' start (triggered in GuiceConfig), it's safe to access them via static getter
 * methods.
 *
 * @author Kristian Lange
 */
@Singleton
public class Common {

    private static final ALogger LOGGER = Logger.of(Common.class);

    private static String jatosVersion;
    private static final String jatosApiVersion = "1.0.1";
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
    private static int userSessionTimeout;
    private static int userSessionInactivity;
    private static boolean userSessionAllowKeepSignedin;
    private static String dbUrl;
    private static String dbDriver;
    private static String dbConnectionPoolSize;
    private static String mac;
    private static int userPasswordLength;
    private static int userPasswordStrength;
    private static String jatosUrlBasePath;
    private static String jatosUpdateMsg;
    private static String jatosHttpAddress;
    private static int jatosHttpPort;
    private static String ldapUrl;
    private static String ldapUserAttribute;
    private static List<String> ldapBaseDn;
    private static String ldapAdminDn;
    private static String ldapAdminPassword;
    private static int ldapTimeout;
    private static String oauthGoogleClientId;
    private static String oidcDiscoveryUrl;
    private static String oidcClientId;
    private static String oidcClientSecret;
    private static List<String> oidcScope;
    private static String oidcUsernameFrom;
    private static String oidcIdTokenSigningAlgorithm;
    private static String oidcSigninButtonText;
    private static String oidcSigninButtonLogoUrl;
    private static String oidcSuccessFeedback;
    private static String orcidDiscoveryUrl;
    private static String orcidClientId;
    private static String orcidClientSecret;
    private static List<String> orcidScope;
    private static String orcidUsernameFrom;
    private static String orcidIdTokenSigningAlgorithm;
    private static String orcidSigninButtonText;
    private static String orcidSigninButtonLogoUrl;
    private static String orcidSuccessFeedback;
    private static String sramDiscoveryUrl;
    private static String sramClientId;
    private static String sramClientSecret;
    private static List<String> sramScope;
    private static String sramUsernameFrom;
    private static String sramIdTokenSigningAlgorithm;
    private static String sramSigninButtonText;
    private static String sramSigninButtonLogoUrl;
    private static String sramSuccessFeedback;
    private static String conextDiscoveryUrl;
    private static String conextClientId;
    private static String conextClientSecret;
    private static List<String> conextScope;
    private static String conextUsernameFrom;
    private static String conextIdTokenSigningAlgorithm;
    private static String conextSigninButtonText;
    private static String conextSigninButtonLogoUrl;
    private static String conextSuccessFeedback;
    private static boolean donationAllowed;
    private static String termsOfUseUrl;
    private static String brandingUrl;
    private static String locale;
    private static boolean studyMembersAllowedToAddAllUsers;
    private static boolean idCookiesSecure;
    private static Http.Cookie.SameSite idCookiesSameSite;
    private static int idCookiesLimit;
    private static boolean showStudyAssetsSizeInStudyManager;
    private static boolean showResultDataSizeInStudyManager;
    private static boolean showResultFileSizeInStudyManager;
    private static boolean userRoleAllowSuperuser;
    private static boolean jatosApiAllowed;
    private static String logsPath;
    private static String logsFilename;
    private static String logsAppender;
    private static String tmpPath;
    private static boolean multiNode;
    private static String threadPoolSize;
    private static String studyArchiveSuffix;
    private static String resultsArchiveSuffix;
    private static boolean groupsCleaningAllowed;
    private static int groupsCleaningInterval;
    private static int groupsCleaningMemberIdleAfter;

    /**
     * List of regular expressions and their description as Pairs that define password restrictions
     * (the regexes are from https://stackoverflow.com/questions/19605150)
     */
    private static final List<Pair<String, String>> userPasswordStrengthRegexList = Arrays.asList(
            Pair.of("The password has no further restrictions on the type of characters.", "^.*$"),
            Pair.of("The password must have at least one Latin letter and one number.",
                    "^(?=.*?[A-Za-z])(?=.*?[0-9]).{2,}$"),
            Pair.of("The password must have at least one Latin letter, one number and one special character (#?!@$%^&*-).",
                    "^(?=.*?[A-Za-z])(?=.*?[0-9])(?=.*?[#?!@$%^&*-]).{3,}$"),
            Pair.of("The password must have at least one uppercase Latin letter, one lowercase Latin letter, one number and one special character (#?!@$%^&*-).",
                    "^(?=.*?[a-z])(?=.*?[A-Z])(?=.*?[0-9])(?=.*?[#?!@$%^&*-]).{4,}"));

    @Inject
    Common(Config config) {
        jatosVersion = BuildInfo.version();
        basepath = config.getString("play.server.dir");
        studyAssetsRootPath = obtainPath(config, "jatos.studyAssetsRootPath");
        LOGGER.info("Path to study assets directory is " + studyAssetsRootPath);
        studyLogsEnabled = config.getBoolean("jatos.studyLogs.enabled");
        studyLogsPath = obtainPath(config, "jatos.studyLogs.path");
        LOGGER.info("Path to study logs directory is " + studyLogsPath);
        resultUploadsEnabled = config.getBoolean("jatos.resultUploads.enabled");
        resultUploadsPath = obtainPath(config, "jatos.resultUploads.path");
        LOGGER.info("Path to uploads directory is " + resultUploadsPath);
        resultUploadsMaxFileSize = config.getBytes("jatos.resultUploads.maxFileSize");
        resultUploadsLimitPerStudyRun = config.getBytes("jatos.resultUploads.limitPerStudyRun");
        resultDataMaxSize = config.getBytes("jatos.resultData.maxSize");
        maxResultsDbQuerySize = config.getInt("jatos.maxResultsDbQuerySize");
        userSessionTimeout = config.getInt("jatos.userSession.timeout");
        userSessionInactivity = config.getInt("jatos.userSession.inactivity");
        userSessionAllowKeepSignedin = config.getBoolean("jatos.userSession.allowKeepSignedin");
        dbUrl = config.getString("db.default.url"); // Also jatos.db.url
        dbDriver = config.getString("db.default.driver"); // Also jatos.db.driver
        dbConnectionPoolSize = config.getString("jatos.db.connectionPool.size");
        mac = fillMac();
        userPasswordLength = config.getInt("jatos.user.password.length");
        userPasswordStrength = config.getInt("jatos.user.password.strength");
        if (userPasswordStrength > userPasswordStrengthRegexList.size()) {
            userPasswordStrength = 0;
        }
        jatosUrlBasePath = config.getString("play.http.context"); // Also jatos.urlBasePath
        jatosUpdateMsg = !config.getIsNull("jatos.update.msg") ? config.getString("jatos.update.msg") : null;
        jatosHttpAddress = config.getString("play.server.http.address"); // Also jatos.http.address
        if (jatosHttpAddress.equals("0.0.0.0")) jatosHttpAddress = "127.0.0.1"; // Fix localhost IP
        jatosHttpPort = config.getInt("play.server.http.port"); // Also jatos.http.port
        ldapUrl = config.getString("jatos.user.authentication.ldap.url");
        ldapUserAttribute = config.getString("jatos.user.authentication.ldap.userAttribute");
        if (config.getValue("jatos.user.authentication.ldap.basedn").valueType() == ConfigValueType.STRING) {
            ldapBaseDn = Collections.singletonList(config.getString("jatos.user.authentication.ldap.basedn"));
        } else if (config.getValue("jatos.user.authentication.ldap.basedn").valueType() == ConfigValueType.LIST) {
            ldapBaseDn = config.getStringList("jatos.user.authentication.ldap.basedn");
        }
        ldapAdminDn = config.getString("jatos.user.authentication.ldap.admin.dn");
        ldapAdminPassword = config.getString("jatos.user.authentication.ldap.admin.password");
        ldapTimeout = config.getInt("jatos.user.authentication.ldap.timeout");
        oauthGoogleClientId = config.getString("jatos.user.authentication.oauth.googleClientId");
        oidcDiscoveryUrl = config.getString("jatos.user.authentication.oidc.discoveryUrl");
        oidcClientId = config.getString("jatos.user.authentication.oidc.clientId");
        oidcClientSecret = config.getString("jatos.user.authentication.oidc.clientSecret");
        oidcScope = config.getStringList("jatos.user.authentication.oidc.scope");
        oidcUsernameFrom = config.getString("jatos.user.authentication.oidc.usernameFrom");
        oidcIdTokenSigningAlgorithm = config.getString("jatos.user.authentication.oidc.idTokenSigningAlgorithm");
        oidcSigninButtonText = config.getString("jatos.user.authentication.oidc.signInButtonText");
        oidcSigninButtonLogoUrl = config.getString("jatos.user.authentication.oidc.signInButtonLogoUrl");
        oidcSuccessFeedback = config.getString("jatos.user.authentication.oidc.successFeedback");
        orcidDiscoveryUrl = config.getString("jatos.user.authentication.orcid.discoveryUrl");
        orcidClientId = config.getString("jatos.user.authentication.orcid.clientId");
        orcidClientSecret = config.getString("jatos.user.authentication.orcid.clientSecret");
        orcidScope = config.getStringList("jatos.user.authentication.orcid.scope");
        orcidUsernameFrom = config.getString("jatos.user.authentication.orcid.usernameFrom");
        orcidIdTokenSigningAlgorithm = config.getString("jatos.user.authentication.orcid.idTokenSigningAlgorithm");
        orcidSigninButtonText = config.getString("jatos.user.authentication.orcid.signInButtonText");
        orcidSigninButtonLogoUrl = config.getString("jatos.user.authentication.orcid.signInButtonLogoUrl");
        orcidSuccessFeedback = config.getString("jatos.user.authentication.orcid.successFeedback");
        sramDiscoveryUrl = config.getString("jatos.user.authentication.sram.discoveryUrl");
        sramClientId = config.getString("jatos.user.authentication.sram.clientId");
        sramClientSecret = config.getString("jatos.user.authentication.sram.clientSecret");
        sramScope = config.getStringList("jatos.user.authentication.sram.scope");
        sramUsernameFrom = config.getString("jatos.user.authentication.sram.usernameFrom");
        sramIdTokenSigningAlgorithm = config.getString("jatos.user.authentication.sram.idTokenSigningAlgorithm");
        sramSigninButtonText = config.getString("jatos.user.authentication.sram.signInButtonText");
        sramSigninButtonLogoUrl = config.getString("jatos.user.authentication.sram.signInButtonLogoUrl");
        sramSuccessFeedback = config.getString("jatos.user.authentication.sram.successFeedback");
        conextDiscoveryUrl = config.getString("jatos.user.authentication.conext.discoveryUrl");
        conextClientId = config.getString("jatos.user.authentication.conext.clientId");
        conextClientSecret = config.getString("jatos.user.authentication.conext.clientSecret");
        conextScope = config.getStringList("jatos.user.authentication.conext.scope");
        conextUsernameFrom = config.getString("jatos.user.authentication.conext.usernameFrom");
        conextIdTokenSigningAlgorithm = config.getString("jatos.user.authentication.conext.idTokenSigningAlgorithm");
        conextSigninButtonText = config.getString("jatos.user.authentication.conext.signInButtonText");
        conextSigninButtonLogoUrl = config.getString("jatos.user.authentication.conext.signInButtonLogoUrl");
        conextSuccessFeedback = config.getString("jatos.user.authentication.conext.successFeedback");
        donationAllowed = config.getBoolean("jatos.donationAllowed");
        termsOfUseUrl = config.getString("jatos.termsOfUseUrl");
        brandingUrl = config.getString("jatos.brandingUrl");
        locale = config.getString("jatos.locale");
        studyMembersAllowedToAddAllUsers = config.getBoolean("jatos.studyMembers.allowAddAllUsers");
        idCookiesSecure = config.getBoolean("jatos.idCookies.secure");
        idCookiesSameSite = fillIdCookiesSameSite(config);
        idCookiesLimit = config.getInt("jatos.idCookies.limit");
        showStudyAssetsSizeInStudyManager = config.getBoolean("jatos.studyAdmin.showStudyAssetsSize");
        showResultDataSizeInStudyManager = config.getBoolean("jatos.studyAdmin.showResultDataSize");
        showResultFileSizeInStudyManager = config.getBoolean("jatos.studyAdmin.showResultFileSize");
        userRoleAllowSuperuser = config.getBoolean("jatos.user.role.allowSuperuser");
        jatosApiAllowed = config.getBoolean("jatos.api.allowed");
        logsPath = obtainPath(config, "jatos.logs.path");
        LOGGER.info("Path to logs directory is " + logsPath);
        logsFilename = config.getString("jatos.logs.filename");
        logsAppender = config.getString("jatos.logs.appender");
        multiNode = config.getBoolean("jatos.multiNode");
        tmpPath = config.getIsNull("jatos.tmpPath")
                ? System.getProperty("java.io.tmpdir") + File.separator + "jatos"
                : obtainPath(config, "jatos.tmpPath");
        LOGGER.info("Path to tmp directory is " + tmpPath);
        threadPoolSize = config.getString("jatos.threadPool.size");
        studyArchiveSuffix = config.getString("jatos.studyArchive.suffix");
        resultsArchiveSuffix = config.getString("jatos.resultsArchive.suffix");
        groupsCleaningAllowed = config.getBoolean("jatos.groups.cleaning.allowed");
        groupsCleaningInterval = config.getInt("jatos.groups.cleaning.interval");
        groupsCleaningMemberIdleAfter = config.getInt("jatos.groups.cleaning.memberIdleAfter");
    }

    /**
     * Resolves a configured filesystem path into an absolute, canonical path usable by the runtime.
     * The returned path is always absolute and normalized for the host operating system.
     */
    private String obtainPath(Config config, String property) {
        String path = config.getString(property);
        // Replace ~ with actual home directory
        path = path.replace("~", System.getProperty("user.home"));
        // Replace Unix-like file separator with the actual system's one
        path = path.replace("/", File.separator);
        // If it is a relative path, add JATOS' base path as a prefix
        if (!(new File(path).isAbsolute())) {
            path = basepath + File.separator + path;
        }
        // Turn into a canonical path (e.g., remove '.' and '..')
        try {
            path = new File(path).getCanonicalFile().toString();
        } catch (IOException e) {
            throw new RuntimeException("Error in config property " + property + "=" + path);
        }
        return path;
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
        } else if (idCookiesSameSiteRaw.equalsIgnoreCase("none")) {
            return Http.Cookie.SameSite.NONE;
        } else {
            return null;
        }
    }

    /**
     * JATOS version (full version e.g. v3.5.5-alpha)
     */
    public static String getJatosVersion() {
        return jatosVersion;
    }

    /**
     * JATOS API version (different from JATOS version)
     */
    public static String getJatosApiVersion() {
        return jatosApiVersion;
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
     * Time in minutes when the Play session will timeout (defined in
     * application.conf)
     */
    public static int getUserSessionTimeout() {
        return userSessionTimeout;
    }

    /**
     * Time in minutes a user can be inactive before they will be signed out
     * (defined in application.conf)
     */
    public static int getUserSessionInactivity() {
        return userSessionInactivity;
    }

    /**
     * If true, the user has the possibility (a checkbox on the GUI's signin page) to keep the user session and to not
     * get signed out automatically due to user a session timeout (neither through normal timeout nor inactivity).
     * If set to true and the user chooses to keep being signed in, the user session is kept until the user signs out
     * manually or the session cookie is deleted.
     */
    public static boolean getUserSessionAllowKeepSignedin() {
        return userSessionAllowKeepSignedin;
    }

    /**
     * Database URL as defined in application.conf
     */
    public static String getDbUrl() {
        return dbUrl;
    }

    /**
     * Does JATOS use an MySQL database?
     */
    public static boolean usesMysql() {
        return getDbUrl().toLowerCase().contains("jdbc:mysql");
    }

    /**
     * Database driver as defined in application.conf
     */
    public static String getDbDriver() {
        return dbDriver;
    }

    /**
     * Database connection pool size
     */
    public static String getDbConnectionPoolSize() {
        return dbConnectionPoolSize;
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
    public static String getJatosUrlBasePath() {
        return jatosUrlBasePath;
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
     * LDAP User attribute, e.g. 'uid' or 'cn'
     */
    public static String getLdapUserAttribute() {
        return ldapUserAttribute;
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
     * OpenId Connect (OIDC) allowed
     */
    public static boolean isOidcAllowed() {
        return !Strings.isNullOrEmpty(oidcClientId);
    }

    /**
     * OpenId Connect (OIDC) provider discovery URL (ends with ".well-known/openid-configuration")
     */
    public static String getOidcDiscoveryUrl() {
        return oidcDiscoveryUrl;
    }

    /**
     * OpenId Connect (OIDC) client ID
     */
    public static String getOidcClientId() {
        return oidcClientId;
    }

    /**
     * OpenId Connect (OIDC) client secret. Can be null if not used.
     */
    public static String getOidcClientSecret() {
        return oidcClientSecret;
    }

    /**
     * OpenId Connect (OIDC) scope (e.g. "openid", "profile", "email")
     */
    public static List<String> getOidcScope() {
        return oidcScope;
    }

    /**
     * OpenId Connect (OIDC) - Where should JATOS' username be taken from?
     */
    public static String getOidcUsernameFrom() {
        return oidcUsernameFrom;
    }

    /**
     * OpenId Connect (OIDC) token signing algorithm (e.g. RS256)
     */
    public static String getOidcIdTokenSigningAlgorithm() {
        return oidcIdTokenSigningAlgorithm;
    }

    /**
     * Text of OIDC button in sign-in page
     */
    public static String getOidcSigninButtonText() {
        return oidcSigninButtonText;
    }

    /**
     * Logo URL of OIDC button in sign-in page
     */
    public static String getOidcSigninButtonLogoUrl() {
        return oidcSigninButtonLogoUrl;
    }

    /**
     * Success feedback text shown to the user if OIDC sign-in was successful
     */
    public static String getOidcSuccessFeedback() {
        return oidcSuccessFeedback;
    }

    public static boolean isOrcidAllowed() {
        return !Strings.isNullOrEmpty(orcidClientId);
    }

    /**
     * ORCID's OpenId Connect (OIDC) provider discovery URL (ends with ".well-known/openid-configuration")
     */
    public static String getOrcidDiscoveryUrl() {
        return orcidDiscoveryUrl;
    }

    /**
     * ORCID'S OpenId Connect (OIDC) client ID
     */
    public static String getOrcidClientId() {
        return orcidClientId;
    }

    /**
     * ORCID's OpenId Connect (OIDC) client secret.
     */
    public static String getOrcidClientSecret() {
        return orcidClientSecret;
    }

    /**
     * ORCID's OpenId Connect (OIDC) scope (e.g. "openid", "profile", "email")
     */
    public static List<String> getOrcidScope() {
        return orcidScope;
    }

    /**
     * ORCID's OpenId Connect (OIDC) - Where should JATOS' username be taken from?
     */
    public static String getOrcidUsernameFrom() {
        return orcidUsernameFrom;
    }

    /**
     * ORCID's OpenId Connect (OIDC) token signing algorithm (e.g. RS256)
     */
    public static String getOrcidIdTokenSigningAlgorithm() {
        return orcidIdTokenSigningAlgorithm;
    }

    /**
     * Text of ORCID button on the sign-in page
     */
    public static String getOrcidSigninButtonText() {
        return orcidSigninButtonText;
    }

    /**
     * Logo URL of ORCID button on the sign-in page
     */
    public static String getOrcidSigninButtonLogoUrl() {
        return orcidSigninButtonLogoUrl;
    }

    /**
     * Success feedback text shown to the user if ORCID sign-in was successful.
     */
    public static String getOrcidSuccessFeedback() {
        return orcidSuccessFeedback;
    }

    /**
     * SRAM's OpenId Connect (OIDC) allowed
     */
    public static boolean isSramAllowed() {
        return !Strings.isNullOrEmpty(sramClientId);
    }

    /**
     * SRAM's OpenId Connect (OIDC) provider discovery URL (ends with ".well-known/openid-configuration")
     */
    public static String getSramDiscoveryUrl() {
        return sramDiscoveryUrl;
    }

    /**
     * SRAM'S OpenId Connect (OIDC) client ID
     */
    public static String getSramClientId() {
        return sramClientId;
    }

    /**
     * SRAM's OpenId Connect (OIDC) client secret.
     */
    public static String getSramClientSecret() {
        return sramClientSecret;
    }

    /**
     * SRAM's OpenId Connect (OIDC) scope (e.g. "openid", "profile", "email")
     */
    public static List<String> getSramScope() {
        return sramScope;
    }

    /**
     * SRAM's OpenId Connect (OIDC) - Where should JATOS' username be taken from?
     */
    public static String getSramUsernameFrom() {
        return sramUsernameFrom;
    }

    /**
     * SRAM's OpenId Connect (OIDC) token signing algorithm (e.g. RS256)
     */
    public static String getSramIdTokenSigningAlgorithm() {
        return sramIdTokenSigningAlgorithm;
    }

    /**
     * Text of SRAM button on the sign-in page
     */
    public static String getSramSigninButtonText() {
        return sramSigninButtonText;
    }

    /**
     * Logo URL of SRAM button on the sign-in page
     */
    public static String getSramSigninButtonLogoUrl() {
        return sramSigninButtonLogoUrl;
    }

    /**
     * Success feedback text shown to the user if SRAM sign-in was successful.
     */
    public static String getSramSuccessFeedback() {
        return sramSuccessFeedback;
    }

    /**
     * SURFconext OpenId Connect (OIDC) allowed
     */
    public static boolean isConextAllowed() {
        return !Strings.isNullOrEmpty(conextClientId);
    }

    /**
     * SURFconext OpenId Connect (OIDC) provider discovery URL (ends with ".well-known/openid-configuration")
     */
    public static String getConextDiscoveryUrl() {
        return conextDiscoveryUrl;
    }

    /**
     * SURFconext OpenId Connect (OIDC) client ID
     */
    public static String getConextClientId() {
        return conextClientId;
    }

    /**
     * SURFconext OpenId Connect (OIDC) client secret.
     */
    public static String getConextClientSecret() {
        return conextClientSecret;
    }

    /**
     * SURFconext OpenId Connect (OIDC) scope (e.g. "openid", "profile", "email").
     * SURFconext ignores scopes other than "openid" (see: https://servicedesk.surf.nl/wiki/spaces/IAM/pages/128909987/OpenID+Connect+features#OpenIDConnectfeatures-Scopes)
     */
    public static List<String> getConextScope() {
        return conextScope;
    }

    /**
     * SURFconext OpenId Connect (OIDC) - Where should JATOS' username be taken from?
     */
    public static String getConextUsernameFrom() {
        return conextUsernameFrom;
    }

    /**
     * SURFconext OpenId Connect (OIDC) token signing algorithm (e.g. RS256)
     */
    public static String getConextIdTokenSigningAlgorithm() {
        return conextIdTokenSigningAlgorithm;
    }

    /**
     * Text of SURFconext button on the sign-in page
     */
    public static String getConextSigninButtonText() {
        return conextSigninButtonText;
    }

    /**
     * Logo URL of SURFconext button on the sign-in page
     */
    public static String getConextSigninButtonLogoUrl() {
        return conextSigninButtonLogoUrl;
    }

    /**
     * Success feedback text shown to the user if SRAM sign-in was successful.
     */
    public static String getConextSuccessFeedback() {
        return conextSuccessFeedback;
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
    public static String getTermsOfUseUrl() {
        return termsOfUseUrl;
    }

    /**
     * URL where some static HTML can be found that can be shown instead of the default welcome message on the home page
     */
    public static String getBrandingUrl() {
        return brandingUrl;
    }

    public static boolean hasBranding() {
        return !Strings.isNullOrEmpty(brandingUrl);
    }

    /**
     * Locale used in the GUI. If not set, the browser's 'navigator.language' is used.
     */
    public static String getLocale() {
        return locale;
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
     * Max number of ID cookies
     */
    public static int getIdCookiesLimit() {
        return idCookiesLimit;
    }

    /**
     * If false, the study assets folder size won't be calculated for the study manager page. Sometimes the filesystem is too slow to allow this.
     */
    public static boolean showStudyAssetsSizeInStudyManager() {
        return showStudyAssetsSizeInStudyManager;
    }

    /**
     * If false, the result data size won't be calculated for the study manager page. Sometime the database is too slow to allow this.
     */
    public static boolean showResultDataSizeInStudyManager() {
        return showResultDataSizeInStudyManager;
    }

    /**
     * If false, the study result file size won't be calculated for the study manager page. Sometimes the filesystem is too slow to allow this.
     */
    public static boolean showResultFileSizeInStudyManager() {
        return showResultFileSizeInStudyManager;
    }

    /**
     * If true, it is allowed to grant users the Superuser role
     */
    public static boolean isUserRoleAllowSuperuser() {
        return userRoleAllowSuperuser;
    }

    /**
     * If true, it is allowed to use JATOS' API
     */
    public static boolean isJatosApiAllowed() {
        return jatosApiAllowed;
    }

    /**
     * Path where the application logs are located
     */
    public static String getLogsPath() {
        return logsPath;
    }

    /**
     * Base name of JATOS log files without the suffix ('.log' or '.gz'). Default is 'application'.
     */
    public static String getLogsFilename() {
        return logsFilename + ".log";
    }

    /**
     * Log appender: can be 'ASYNCFILE' (default) or 'ASYNCSTDOUT'
     */
    public static String getLogsAppender() {
        return logsAppender;
    }

    /**
     *
     * Returns true if the Logger logs to STDOUT and false otherwise.
     */
    public static boolean isLogsAppenderStdOut() {
        return "ASYNCSTDOUT".equals(logsAppender) || "STDOUT".equals(logsAppender);
    }

    /**
     * Path to the JATOS tmp directory. If not set, it is System.getProperty("java.io.tmpdir").
     */
    public static String getTmpPath() {
        return tmpPath;
    }

    /**
     * True indicates that this JATOS runs with others in a cluster
     */
    public static boolean isMultiNode() {
        return multiNode;
    }

    /**
     * JATOS' thread pool size
     */
    public static String getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * File extension of study archive files
     */
    public static String getStudyArchiveSuffix() {
        return studyArchiveSuffix;
    }

    /**
     * File extension of results archive files
     */
    public static String getResultsArchiveSuffix() {
        return resultsArchiveSuffix;
    }

    /**
     * True if the group cleaning is allowed
     */
    public static boolean isGroupsCleaningAllowed() {
        return groupsCleaningAllowed;
    }

    /**
     * Interval in seconds the group cleaner is started.
     */
    public static int getGroupsCleaningInterval() {
        return groupsCleaningInterval;
    }

    /**
     * After how many seconds a group member is regarded as idle.
     */
    public static int getGroupsCleaningMemberIdleAfter() {
        return groupsCleaningMemberIdleAfter;
    }
}
