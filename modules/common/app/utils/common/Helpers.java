package utils.common;

import general.common.Common;
import models.common.User;
import org.apache.commons.io.FileUtils;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import play.Logger;
import play.Logger.ALogger;
import play.api.mvc.RequestHeader;
import play.mvc.Controller;
import play.mvc.Http;
import scala.Option;

import java.io.UnsupportedEncodingException;
import java.lang.management.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.CharacterIterator;
import java.text.SimpleDateFormat;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Kristian Lange
 */
public class Helpers {

    private static final ALogger LOGGER = Logger.of(Helpers.class);

    /**
     * Check if the request was made via Ajax or not for Scala requests.
     */
    public static boolean isAjax(RequestHeader request) {
        Option<String> headerOption = request.headers().get("X-Requested-With");
        return headerOption.isDefined() && headerOption.get().equals("XMLHttpRequest");
    }

    /**
     * Check if the request was made via Ajax or not.
     */
    public static Boolean isAjax() {
        return Controller.request().header("X-Requested-With").map(v -> v.equals("XMLHttpRequest")).orElse(false);
    }

    /**
     * Returns the request's host URL without path (and without base path from 'play.http.context') or query string
     * (e.g. "https://www.example.com"). It returns the URL with the proper protocol http or https.
     * If JATOS is run behind a proxy the real host address must be passed on with X-Forwarded-For header.
     * See: https://www.playframework.com/documentation/2.8.x/HTTPServer#Forwarded-header-version
     */
    public static URL getRealHostUrl(Http.Request request) {
        try {
            String protocol = getRequestsProtocol();
            return new URL(protocol + "://" + request.host());
        } catch (MalformedURLException e) {
            LOGGER.error(".getRealHostUrl: couldn't get request's host URL", e);
            return null;
        }
    }

    /**
     * Returns the request's host URL with base path from 'play.http.context' but without the rest of the path
     * or query string (e.g. "https://www.example.com/basepath/"). It returns the URL with the proper protocol http
     * or https. If JATOS is run behind a proxy the real host address must be passed on with X-Forwarded-For header.
     * See: https://www.playframework.com/documentation/2.8.x/HTTPServer#Forwarded-header-version
     */
    public static URL getRealBaseUrl(Http.Request request) {
        try {
            String protocol = getRequestsProtocol();
            return new URL(protocol + "://" + request.host() + Common.getPlayHttpContext());
        } catch (MalformedURLException e) {
            LOGGER.error(".getRealBaseUrl: error in base URL", e);
            return null;
        }
    }

    public static boolean isLocalhost() {
        String host = Controller.request().host();
        Optional<String> referer = Controller.request().header("referer");
        boolean isHostLocalhost = host != null && (host.matches("localhost:?\\d*") || host.contains("127.0.0.1") || host
                .contains("0.0.0.0") || host.equals("::1"));
        boolean isRefererLocalhost = referer.map(r ->
                r.matches("localhost:?\\d*")
                        || r.contains("127.0.0.1")
                        || r.contains("0.0.0.0")
                        || r.equals("::1"))
                .orElse(false);
        return isHostLocalhost || isRefererLocalhost;
    }

    /**
     * Returns the request's protocol, either 'http' or 'https'. It determines the protocol by looking at three things:
     * 1) it checks if the HTTP header 'X-Forwarded-Proto' is set and equals 'https', 2) it checks if the HTTP header
     * 'Referer' starts with https, 3) it uses Play's RequestHeader.secure() method. The 'X-Forwarded-Proto' header may
     * be set by proxies/load balancers in front of JATOS. On Amazon's AWS load balancer with HTTPS/SSL the
     * 'X-Forwarded-Proto' is not set and to still determine the right protocol we use the Referer as last option.
     */
    private static String getRequestsProtocol() {
        boolean isXForwardedProtoHttps = Controller.request().header("X-Forwarded-Proto").map(h -> h.equals("https"))
                .orElse(false);
        boolean isRefererProtoHttps = Controller.request().header("Referer").map(h -> h.startsWith("https")).orElse(
                false);
        return isXForwardedProtoHttps || isRefererProtoHttps || Controller.request().secure() ? "https" : "http";
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

        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        info.put("Uptime", "" + humanReadableDuration(Duration.ofMillis(runtimeBean.getUptime())));
        info.put("Name", runtimeBean.getName());
        info.put("PID", runtimeBean.getName().split("@")[0]);
        info.put("Java name", runtimeBean.getVmName());
        info.put("Java version", System.getProperty("java.version"));

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        info.put("Thread count", "" + threadBean.getThreadCount());
        info.put("Peak thread count", "" + threadBean.getPeakThreadCount());

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
        info.put("OS name", "" + systemBean.getName());
        info.put("OS version", "" + systemBean.getVersion());
        info.put("System load average", "" + systemBean.getSystemLoadAverage());
        info.put("Available processors", "" + systemBean.getAvailableProcessors());
        return info;
    }

    public static String humanReadableDuration(Duration duration) {
        return duration.toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
    }

    public static String formatDate(Date date) {
        return date != null ? (new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")).format(date) : "never";
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

}
