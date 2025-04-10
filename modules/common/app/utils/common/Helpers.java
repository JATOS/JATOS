package utils.common;

import akka.stream.IOResult;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.google.common.base.Strings;
import general.common.Common;
import models.common.User;
import org.apache.commons.io.FileUtils;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import play.api.mvc.RequestHeader;
import play.mvc.Controller;
import play.mvc.Http;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.management.*;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.CharacterIterator;
import java.text.SimpleDateFormat;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
public class Helpers {

    /**
     * Check if the request was made via Ajax or not.
     */
    public static Boolean isAjax() {
        return Controller.request().header("X-Requested-With").map(v -> v.equals("XMLHttpRequest")).orElse(false);
    }

    public static boolean isHtmlRequest(Http.Request request) {
        return request.accepts(Http.MimeTypes.HTML);
    }

    public static boolean isHtmlRequest(RequestHeader request) {
        return request.asJava().getHeaders().get("Accept").map(s -> s.toLowerCase().contains("html")).orElse(false);
    }

    /**
     * Checks if the session has a field 'username'
     */
    public static boolean isSessionCookieRequest(Http.Request request) {
        return request.cookie("PLAY_SESSION") != null && !Strings.isNullOrEmpty(request.cookie("PLAY_SESSION").value());
    }

    /**
     * Checks if the HTTP request has an "Authorization: Bearer" header. This does not check any authentication.
     */
    public static boolean isApiRequest(Http.Request request) {
        Optional<String> headerOptional = request.header("Authorization");
        return headerOptional.isPresent() && headerOptional.get().contains("Bearer");
    }

    public static String getLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "Problem getting local IP";
        }
    }

    public static String urlEncode(String str) {
        String encodedStr = "";
        try {
            encodedStr = URLEncoder.encode(str, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // Do nothing
        }
        return encodedStr;
    }

    public static String urlDecode(String str) {
        if (str == null) return null;
        String decodedStr = null;
        try {
            decodedStr = URLDecoder.decode(str, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // Do nothing
        }
        return decodedStr;
    }

    /**
     * Gets the value of to the given parameter in request's query string and trims whitespace.
     */
    public static String getQueryParameter(Http.Request request, String parameter) {
        String value = request.getQueryString(parameter);
        if (value != null) {
            value = value.trim();
        }
        return value;
    }

    /**
     * Returns the whole query string of the given Request including '?'.
     */
    public static String getQueryString(Http.Request request) {
        return request.queryString().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()[0])
                .collect(Collectors.joining("&", "?", ""));
    }

    public static String humanReadableByteCount(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format("%.1f %cB", bytes / 1000.0, ci.current());
    }

    public static Map<String, String> getJVMInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("User", System.getProperty("user.name"));

        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        info.put("Uptime", humanReadableDuration(Duration.ofMillis(runtimeBean.getUptime())));
        info.put("Name", runtimeBean.getName());
        info.put("PID", runtimeBean.getName().split("@")[0]);
        info.put("Java name", runtimeBean.getVmName());
        info.put("Java version", System.getProperty("java.version"));

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        info.put("Thread count", String.valueOf(threadBean.getThreadCount()));
        info.put("Peak thread count", String.valueOf(threadBean.getPeakThreadCount()));

        // Using Runtime.getRuntime()
        info.put("Total memory", humanReadableByteCount(Runtime.getRuntime().totalMemory()));
        info.put("Free memory", humanReadableByteCount(Runtime.getRuntime().freeMemory()));
        info.put("Used memory",
                humanReadableByteCount(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
        info.put("Max memory", humanReadableByteCount(Runtime.getRuntime().maxMemory()));

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        info.put("Heap memory used", FileUtils.byteCountToDisplaySize(memoryBean.getHeapMemoryUsage().getUsed()));
        info.put("Non-heap memory used",
                FileUtils.byteCountToDisplaySize(memoryBean.getNonHeapMemoryUsage().getUsed()));
        return info;
    }

    public static Map<String, String> getOSInfo() {
        Map<String, String> info = new LinkedHashMap<>();

        OperatingSystemMXBean systemBean = ManagementFactory.getOperatingSystemMXBean();
        info.put("OS name", systemBean.getName());
        info.put("OS version", systemBean.getVersion());
        info.put("System load average", String.valueOf(systemBean.getSystemLoadAverage()));
        info.put("Available processors", String.valueOf(systemBean.getAvailableProcessors()));
        info.put("System time", LocalDateTime.now().toString());
        return info;
    }

    public static Map<String, String> getJatosConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("Multi node", String.valueOf(Common.isMultiNode()));
        config.put("Local IP", getLocalIpAddress());
        config.put("Local basepath", Common.getBasepath());
        config.put("Logs path", Common.getLogsPath());
        config.put("Logs filename", Common.getLogsFilename());
        config.put("Logs appender", Common.getLogsAppender());
        config.put("Tmp path", Common.getTmpPath());
        config.put("Study assets root path", Common.getStudyAssetsRootPath());
        config.put("Result data max size", humanReadableByteCount(Common.getResultDataMaxSize()));
        config.put("Result uploads allowed", String.valueOf(Common.isResultUploadsEnabled()));
        config.put("Result uploads path", Common.getResultUploadsPath());
        config.put("Result uploads max file size", humanReadableByteCount(Common.getResultUploadsMaxFileSize()));
        config.put("Result uploads limit per study run", humanReadableByteCount(Common.getResultUploadsLimitPerStudyRun()));
        config.put("Study logs allowed", String.valueOf(Common.isStudyLogsEnabled()));
        config.put("Study logs path", String.valueOf(Common.getStudyLogsPath()));
        config.put("User session timeout", String.valueOf(Common.getUserSessionTimeout()));
        config.put("User session inactivity", String.valueOf(Common.getUserSessionInactivity()));
        config.put("DB URL", Common.getDbUrl());
        config.put("DB driver", Common.getDbDriver());
        config.put("Max results DB query size", String.valueOf(Common.getMaxResultsDbQuerySize()));
        config.put("Google OAuth allowed", String.valueOf(Common.isOauthGoogleAllowed()));
        if (Common.isOauthGoogleAllowed()) {
            config.put("Google OAuth client ID", Common.getOauthGoogleClientId());
        }
        config.put("OIDC allowed", String.valueOf(Common.isOidcAllowed()));
        if (Common.isOidcAllowed()) {
            config.put("OIDC discovery URL", Common.getOidcDiscoveryUrl());
            config.put("OIDC client ID", Common.getOidcClientId());
        }
        config.put("ORCID allowed", String.valueOf(Common.isOrcidAllowed()));
        if (Common.isOrcidAllowed()) {
            config.put("ORCID client ID", Common.getOrcidClientId());
        }
        config.put("SRAM allowed", String.valueOf(Common.isSramAllowed()));
        if (Common.isSramAllowed()) {
            config.put("SRAM client ID", Common.getSramClientId());
        }
        config.put("CONEXT allowed", String.valueOf(Common.isConextAllowed()));
        if (Common.isConextAllowed()) {
            config.put("CONEXT client ID", Common.getConextClientId());
        }
        config.put("LDAP allowed", String.valueOf(Common.isLdapAllowed()));
        if (Common.isLdapAllowed()) {
            config.put("LDAP URL", Common.getLdapUrl());
            config.put("LDAP base DN", String.join(", ", Common.getLdapBaseDn()));
            config.put("LDAP admin DN", Common.getLdapAdminDn());
            config.put("LDAP timeout", String.valueOf(Common.getLdapTimeout()));
        }
        return config;
    }

    public static String humanReadableDuration(Duration duration) {
        return duration.toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
    }

    public static String getDateTimeYyyyMMddHHmmss() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }

    /**
     * Initialize all given objects that are loaded lazily in a Hibernate object
     */
    public static void initializeAndUnproxy(Object... objs) {
        Arrays.stream(objs).forEach(Helpers::initializeAndUnproxy);
    }

    /**
     * Initialize an object that is loaded lazily in a Hibernate object
     */
    @SuppressWarnings("unchecked")
    public static <T> T initializeAndUnproxy(T obj) {
        Hibernate.initialize(obj);
        if (obj instanceof HibernateProxy) {
            obj = (T) ((HibernateProxy) obj).getHibernateLazyInitializer().getImplementation();
        }
        return obj;
    }

    public static boolean isAllowedSuperuser(User user) {
        return Common.isUserRoleAllowSuperuser() && user.isSuperuser();
    }

    /**
     * Helper function to allow an action after a file was sent (e.g. delete the file)
     */
    public static Source<ByteString, CompletionStage<IOResult>> okFileStreamed(final File file, final Runnable handler) {
        final Source<ByteString, CompletionStage<IOResult>> fileSource = FileIO.fromFile(file)
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "));
        @SuppressWarnings("UnnecessaryLocalVariable")
        Source<ByteString, CompletionStage<IOResult>> wrap = fileSource.mapMaterializedValue(
                action -> action.whenCompleteAsync((ioResult, exception) -> handler.run()));
        return wrap;
    }

    public static Optional<Long> parseLong(String str) {
        try {
            return Optional.of(Long.parseLong(str));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Gets the number of bytes in UTF-8 the given string has
     */
    public static int getStringSize(String str) {
        return !Strings.isNullOrEmpty(str) ? str.getBytes(StandardCharsets.UTF_8).length : 0;
    }

}
