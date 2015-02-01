package controllers;

import java.net.MalformedURLException;
import java.net.URL;

import play.mvc.Controller;

/**
 * Utility class for all JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
public class ControllerUtils {

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
	 * Same as {@link #getRefererUrl()} but returns the URL's String if the
	 * 'Referer' exists or "" otherwise. Doesn't throw an exception.
	 */
	public static String getReferer() {
		URL refererUrl = null;
		try {
			refererUrl = getRefererUrl();
		} catch (MalformedURLException e) {
			// Do nothing
		}
		return (refererUrl != null) ? refererUrl.toString() : "";
	}

	/**
	 * Returns the request's referer without the path (only protocol, host,
	 * port). Sometimes (e.g. if JATOS is behind a proxy) this is the only way
	 * to get JATOS' absolute URL. If the 'Referer' isn't set in the header it
	 * returns null.
	 */
	public static URL getRefererUrl() throws MalformedURLException {
		URL refererURL = null;
		String[] referer = Controller.request().headers().get("Referer");
		if (referer != null && referer.length > 0) {
			URL refererURLWithPath = new URL(referer[0]);
			refererURL = new URL(refererURLWithPath.getProtocol(),
					refererURLWithPath.getHost(), refererURLWithPath.getPort(),
					"");
		}
		return refererURL;
	}

}
