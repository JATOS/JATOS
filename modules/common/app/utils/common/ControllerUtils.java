package utils.common;

import java.net.MalformedURLException;
import java.net.URL;

import play.Logger;
import play.mvc.Controller;

/**
 * Utility class for all JATOS Controllers.
 * 
 * @author Kristian Lange
 */
public class ControllerUtils {

	private static final String CLASS_NAME = ControllerUtils.class
			.getSimpleName();

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
			String protocol = isXForwardedProtoHttps()
					|| Controller.request().secure() ? "https://" : "http://";
			return new URL(protocol + Controller.request().host());
		} catch (MalformedURLException e) {
			Logger.error(
					CLASS_NAME + ".getRequestUrl: couldn't get request's URL",
					e);
		}
		// Should never happen
		return null;
	}

	/**
	 * Checks if the HTTP header 'X-Forwarded-Proto' is set and equals 'https'.
	 * This header may be set by proxies in front of JATOS.
	 */
	private static boolean isXForwardedProtoHttps() {
		return Controller.request().hasHeader("X-Forwarded-Proto")
				&& Controller.request().headers().get("X-Forwarded-Proto")[0]
						.equals("https");
	}

}
