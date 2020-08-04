package utils.common;

import general.common.Common;
import play.Logger;
import play.Logger.ALogger;
import play.api.mvc.RequestHeader;
import play.mvc.Controller;
import play.mvc.Http;
import scala.Option;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Utility class for all JATOS Controllers.
 *
 * @author Kristian Lange
 */
public class HttpUtils {

    private static final ALogger LOGGER = Logger.of(HttpUtils.class);

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
        boolean isRefererLocalhost = referer.map(
                r -> r.matches("localhost:?\\d*") || r.contains("127.0.0.1") || r.contains("0.0.0.0") || r.equals("::1"))
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
     * Gets the value of to the given key in request's query string and trims whitespace.
     */
    public static String getQueryString(String key) {
        String value = Http.Context.current().request().getQueryString(key);
        if (value != null) {
            value = value.trim();
        }
        return value;
    }

}
