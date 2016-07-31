package services.publix;

import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.publix.Publix;
import exceptions.publix.UnsupportedMediaTypePublixException;
import models.common.StudyResult;
import play.mvc.Call;
import play.mvc.Http.RequestBody;
import play.mvc.Result;
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
	 * Adds the study result ID as URL query string to the call and then
	 * redirects to it.
	 */
	public static Result redirectWithinStudy(Call call,
			StudyResult studyResult) {
		return Publix.redirect(call + "?srid=" + studyResult.getId());
	}

}
