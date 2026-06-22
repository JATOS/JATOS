package http.common;

import com.google.common.base.Strings;
import http.common.Http.Context;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.mvc.Http;

import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility methods for HTTP requests and responses.
 */
public class HttpUtils {

    public static boolean isHtmlRequest(Http.RequestHeader request) {
        return request.getHeaders().get("Accept")
                .map(accept -> Arrays.stream(accept.split(","))
                        .map(String::trim)
                        .map(part -> part.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
                        .anyMatch("text/html"::equals))
                .orElse(false);
    }

    public static boolean isHtmlRequest() {
        return isHtmlRequest(Context.current().requestHeader());
    }

    public static boolean isNotSigninPage() {
        String urlPath = Context.current().requestHeader().path();
        return !urlPath.isEmpty() && !urlPath.matches("(/|/jatos|/jatos/|/jatos/signin|/jatos/signin/)");
    }

    /**
     * Checks if the request has a session cookie
     */
    public static boolean isSessionCookieRequest() {
        Http.RequestHeader request = Context.current().requestHeader();
        return request.getCookie("PLAY_SESSION").isPresent() && !Strings.isNullOrEmpty(request.getCookie("PLAY_SESSION").get().value());
    }

    /**
     * Checks if the HTTP request has an "Authorization: Bearer" header. This does not check any authentication.
     */
    public static boolean isApiRequest() {
        Optional<String> authHeader = Context.current().requestHeader().header("Authorization");
        return authHeader.isPresent() && authHeader.get().startsWith("Bearer ");
    }

    public static String getLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "Problem getting local IP";
        }
    }

    public static String urlEncode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }

    public static String urlDecode(String str) {
        if (str == null) return null;
        return URLDecoder.decode(str, StandardCharsets.UTF_8);
    }

    /**
     * Gets the value of to the given parameter in request's query string and trims whitespace.
     */
    public static String getQueryParameter(String parameter) {
        return Context.current().requestHeader().queryString(parameter).map(String::trim).orElse(null);
    }

    /**
     * Returns the whole query string of the given Request including '?'. Checks for HTML tags to prevent XSS attacks.
     */
    public static String getQueryString() {
        return Context.current().requestHeader().queryString().entrySet().stream()
                .map(e -> {
                    String queryParam = e.getKey() + "=" + e.getValue()[0];
                    if (!Jsoup.isValid(queryParam, Safelist.none())) {
                        throw new IllegalArgumentException("No HTML allowed");
                    }
                    return queryParam;
                })
                .collect(Collectors.joining("&", "?", ""));
    }

}
