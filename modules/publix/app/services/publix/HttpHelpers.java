package services.publix;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.publix.Publix;
import exceptions.publix.UnsupportedMediaTypePublixException;
import models.common.Batch;
import models.common.Component;
import models.common.ComponentResult;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.Worker;
import play.mvc.Http.RequestBody;
import utils.common.XMLUtils;

/**
 * @author Kristian Lange
 */
public abstract class HttpHelpers {

	/**
	 * Generates the value that will be put in the ID cookie. An ID cookie has a
	 * worker ID, study ID, study result ID, group result ID (if not exist:
	 * null), component ID, component result ID and component position.
	 */
	public static String generateIdCookieValue(Batch batch,
			StudyResult studyResult, ComponentResult componentResult,
			Worker worker) {
		Study study = studyResult.getStudy();
		GroupResult groupResult = studyResult.getActiveGroupResult();
		Component component = componentResult.getComponent();
		Map<String, String> cookieMap = new HashMap<>();
		cookieMap.put(Publix.WORKER_ID, String.valueOf(worker.getId()));
		cookieMap.put(Publix.STUDY_ID, String.valueOf(study.getId()));
		cookieMap.put(Publix.STUDY_RESULT_ID,
				String.valueOf(studyResult.getId()));
		String batchId = String.valueOf(batch.getId());
		cookieMap.put(Publix.BATCH_ID, batchId);
		String groupResultId = (groupResult != null)
				? String.valueOf(groupResult.getId()) : "null";
		cookieMap.put(Publix.GROUP_RESULT_ID, groupResultId);
		cookieMap.put(Publix.COMPONENT_ID, String.valueOf(component.getId()));
		cookieMap.put(Publix.COMPONENT_RESULT_ID,
				String.valueOf(componentResult.getId()));
		cookieMap.put(Publix.COMPONENT_POSITION,
				String.valueOf(study.getComponentPosition(component)));
		return generateUrlQueryString(cookieMap);
	}

	/**
	 * Generates a query string as used in an URL. It takes a map and put its
	 * key-value-pairs into a string like in key=value&key=value&...
	 */
	private static String generateUrlQueryString(
			Map<String, String> cookieMap) {
		StringBuilder sb = new StringBuilder();
		Iterator<Entry<String, String>> iterator = cookieMap.entrySet()
				.iterator();
		while (iterator.hasNext()) {
			Entry<String, String> entry = iterator.next();
			sb.append(entry.getKey());
			sb.append("=");
			sb.append(entry.getValue());
			if (iterator.hasNext()) {
				sb.append("&");
			}
		}
		return sb.toString();
	}

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

}
