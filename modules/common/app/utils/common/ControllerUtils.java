package utils.common;

import java.net.MalformedURLException;
import java.net.URL;

import play.Logger;
import play.Logger.ALogger;
import play.mvc.Controller;

/**
 * Utility class for all JATOS Controllers.
 * 
 * @author Kristian Lange
 */
public class ControllerUtils {

	private static final ALogger LOGGER = Logger.of(ControllerUtils.class);

	/**
	 * Check if the request was made via Ajax or not.
	 */
	public static Boolean isAjax() {
		String requestWithHeader = "X-Requested-With";
		String requestWithHeaderValueForAjax = "XMLHttpRequest";
		String[] value = Controller.request().headers().get(requestWithHeader);
		return value != null && value.length > 0
				&& value[0].equals(requestWithHeaderValueForAjax);
	}

	/**
	 * Returns the request's URL without path or query string. It returns the
	 * URL with the proper protocol http or https.
	 */
	public static URL getRequestUrl() {
		try {
			String protocol = getRequestsProtocol();
			return new URL(protocol + "://" + Controller.request().host());
		} catch (MalformedURLException e) {
			LOGGER.error(".getRequestUrl: couldn't get request's URL", e);
		}
		// Should never happen
		return null;
	}

	/**
	 * Returns the request's protocol, either 'http' or 'https'. It determines
	 * the protocol by looking at three things: 1) it checks if the HTTP header
	 * 'X-Forwarded-Proto' is set and equals 'https', 2) it checks if the HTTP
	 * header 'Referer' starts with https, 3) it uses Play's
	 * RequestHeader.secure() method. The 'X-Forwarded-Proto' header may be set
	 * by proxies/load balancers in front of JATOS. On Amazon's AWS load
	 * balancer with HTTPS/SSL the 'X-Forwarded-Proto' is not set and to still
	 * determine the right protocol we use the Referer as last option.
	 */
	private static String getRequestsProtocol() {
		boolean isXForwardedProtoHttps = Controller.request()
				.hasHeader("X-Forwarded-Proto")
				&& Controller.request().headers().get("X-Forwarded-Proto")[0]
						.equals("https");
		boolean isRefererProtoHttps = Controller.request().hasHeader("Referer")
				&& Controller.request().headers().get("Referer")[0]
						.startsWith("https");
		return isXForwardedProtoHttps || isRefererProtoHttps
				|| Controller.request().secure() ? "https" : "http";
	}

}
