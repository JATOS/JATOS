package http.common;

import com.google.common.base.Strings;
import general.common.Common;
import http.common.Http.Context;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.api.mvc.RequestHeader;
import play.mvc.Http;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
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

    public static boolean isHtmlRequest(RequestHeader requestHeader) {
        return isHtmlRequest(requestHeader.asJava());
    }

    public static boolean isGuiUrl(String url) {
        if (url == null) return false;
        String path = URI.create(url).getPath();
        String base = Common.getJatosUrlBasePath().replaceAll("/$", "");
        return Pattern.matches(Pattern.quote(base) + "/jatos(?:$|/(?!api(?:/|$)).*)", path);
    }

    public static boolean isSigninUrl(String url) {
        if (!isGuiUrl(url)) return false;
        String path = URI.create(url).getPath();
        return path.matches(".*/jatos/signin/?");
    }

    /**
     * Checks if the given request is a JATOS API request. An API request has a URL path that starts with
     * '/jatos/api/' after JATOS' configured base URL path.
     */
    public static boolean isApiRequest(Http.RequestHeader request) {
        String base = Common.getJatosUrlBasePath().replaceAll("/$", "");
        return request.path().startsWith(base + "/jatos/api/");
    }

    public static boolean isApiRequest() {
        return isApiRequest(Context.current().requestHeader());
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
    public static boolean hasBearerToken() {
        if (isGuiUrl(Context.current().requestHeader().path())) return false;
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
