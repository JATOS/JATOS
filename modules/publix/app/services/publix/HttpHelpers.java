package services.publix;

import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.JsonNode;

import exceptions.publix.UnsupportedMediaTypePublixException;
import play.mvc.Http.RequestBody;
import utils.common.XMLUtils;

/**
 * @author Kristian Lange
 */
public class HttpHelpers {

	/**
	 * Retrieves the text from the request body and returns it as a String. If
	 * the content is in JSON or XML format it's parsed to bring the String into
	 * a nice format. If the content is neither text nor JSON or XML an
	 * UnsupportedMediaTypePublixException is thrown.
	 */
	public static String getDataFromRequestBody(RequestBody requestBody)
			throws UnsupportedMediaTypePublixException {
		// Text
		String text = requestBody.asText();
		if (text != null) {
			return text;
		}

		// JSON
		JsonNode json = requestBody.asJson();
		if (json != null) {
			return json.toString();
		}

		// XML
		Document xml = requestBody.asXml();
		if (xml != null) {
			return XMLUtils.asString(xml);
		}

		// No supported format
		throw new UnsupportedMediaTypePublixException(
				PublixErrorMessages.SUBMITTED_DATA_UNKNOWN_FORMAT);
	}
	
	/**
	 * Generates an URL with protocol HTTP. Takes the hostname from the request,
	 * the url's path from the given urlPath, and the query string again from
	 * the request.
	 */
	public static String getUrlWithQueryString(String oldUri,
			String requestHost, String newUrlPath) {
		// Check if we have an query string (begins with '?')
		int queryBegin = oldUri.lastIndexOf("?");
		if (queryBegin > 0) {
			String queryString = oldUri.substring(queryBegin + 1);
			newUrlPath = newUrlPath + "?" + queryString;
		}

		// It would be nice if Play has a way to find out which protocol it
		// uses. Apparently it changes http automatically into https if it uses
		// encryption (at least when I checked with Play 2.2.3).
		return "http://" + requestHost + newUrlPath;
	}

}
