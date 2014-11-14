package services;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import models.results.StudyResult;
import models.workers.Worker;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import play.Logger;
import play.Play;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonUtils {

	private static final String DATA = "data";
	private static final String VERSION = "version";
	private static final String CLASS_NAME = JsonUtils.class.getSimpleName();

	/**
	 * ObjectMapper from Jackson JSON library to marshal/unmarshal. It considers
	 * the default timezone.
	 */
	private static final ObjectMapper OBJECTMAPPER = new ObjectMapper()
			.setTimeZone(TimeZone.getDefault());

	/**
	 * Helper class for selectively marshaling an Object to JSON. Only fields of
	 * that Object that are annotated with this class will be serialised. The
	 * intended use is in the public API.
	 */
	public static class JsonForPublix {
	}

	/**
	 * Helper class for selectively marshaling an Object to JSON. Only fields of
	 * that Object that are annotated with this class will be serialised.
	 * Intended use: import/export between different instances of the MechArg.
	 */
	public static class JsonForIO {
	}

	/**
	 * Turns a JSON string into a 'pretty' formatted JSON string suitable for
	 * presentation in the UI. The JSON itself (semantics) aren't changed. If
	 * the JSON string isn't valid it returns null.
	 */
	public static String makePretty(String jsonData) {
		String jsonDataPretty = null;
		try {
			Object json = OBJECTMAPPER.readValue(jsonData, Object.class);
			jsonDataPretty = OBJECTMAPPER.writerWithDefaultPrettyPrinter()
					.writeValueAsString(json);
		} catch (Exception e) {
			Logger.info(CLASS_NAME + ".makePretty: ", e);
		}
		return jsonDataPretty;
	}

	/**
	 * Formats a JSON string into a standardised form suitable for storing into
	 * a DB. If the JSON string isn't valid it returns null.
	 */
	public static String asStringForDB(String jsonDataStr) {
		try {
			String jsonData = OBJECTMAPPER.readTree(jsonDataStr).toString();
			return jsonData;
		} catch (Exception e) {
			Logger.info(CLASS_NAME + ".asStringForDB: ", e);
		}
		return null;
	}

	/**
	 * Checks whether the given string is a valid JSON string.
	 */
	public static boolean isValidJSON(final String jsonDataStr) {
		boolean valid = false;
		try {
			// Parse the string. If an exception occurs return false and true
			// otherwise.
			final JsonParser parser = OBJECTMAPPER.getFactory().createParser(
					jsonDataStr);
			while (parser.nextToken() != null) {
			}
			valid = true;
		} catch (Exception e) {
			Logger.info(CLASS_NAME + ".isValidJSON: ", e);
			valid = false;
		}
		return valid;
	}

	/**
	 * Marshalling an Object into an JSON string. It only considers fields that
	 * are annotated with 'JsonForPublix'.
	 * 
	 * @throws JsonProcessingException
	 */
	public static String asJsonForPublix(Object obj)
			throws JsonProcessingException {
		ObjectWriter objectWriter = OBJECTMAPPER
				.writerWithView(JsonForPublix.class);
		String componentAsJson = objectWriter.writeValueAsString(obj);
		return componentAsJson;
	}

	/**
	 * Returns the data string of a componentResult limited to
	 * MAX_CHAR_PER_RESULT characters.
	 */
	public static String componentResultDataForUI(
			ComponentResult componentResult) throws IOException {
		final int MAX_CHAR_PER_RESULT = 1000;
		String data = componentResult.getData();
		if (data != null) {
			if (!Jsoup.isValid(data, Whitelist.none())) {
				return "Component data contains potentially harmful code won't"
						+ " be displayed here. However you can still export the data.";
			} else if (data.length() < MAX_CHAR_PER_RESULT) {
				return data;
			} else {
				return data.substring(0, MAX_CHAR_PER_RESULT) + " ...";
			}
		} else {
			return "none";
		}
	}

	/**
	 * Returns all studyResults of a study as a JSON string. It's including the
	 * studyResult's componentResults.
	 */
	public static String allStudyResultsForUI(StudyModel study)
			throws IOException {
		ObjectNode allStudyResultsNode = OBJECTMAPPER.createObjectNode();
		ArrayNode arrayNode = allStudyResultsNode.arrayNode();
		List<StudyResult> studyResultList = StudyResult.findAllByStudy(study);
		for (StudyResult studyResult : studyResultList) {
			ObjectNode studyResultNode = studyResultAsJsonNode(studyResult);
			arrayNode.add(studyResultNode);
		}
		allStudyResultsNode.put(DATA, arrayNode);
		String asJsonStr = OBJECTMAPPER.writeValueAsString(allStudyResultsNode);
		return asJsonStr;
	}

	public static String allStudyResultsByWorkerForUI(Worker worker,
			UserModel loggedInUser) throws IOException {
		ObjectNode allStudyResultsNode = OBJECTMAPPER.createObjectNode();
		ArrayNode arrayNode = allStudyResultsNode.arrayNode();

		// Generate the list of StudyResults that the logged-in user is allowed
		// to see
		List<StudyResult> allowedStudyResultList = new ArrayList<StudyResult>();
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().hasMember(loggedInUser)) {
				allowedStudyResultList.add(studyResult);
			}
		}

		// Marshal to JSON
		for (StudyResult studyResult : allowedStudyResultList) {
			ObjectNode studyResultNode = studyResultAsJsonNode(studyResult);
			arrayNode.add(studyResultNode);
		}
		allStudyResultsNode.put(DATA, arrayNode);
		String asJsonStr = OBJECTMAPPER.writeValueAsString(allStudyResultsNode);
		return asJsonStr;
	}

	public static String allComponentResultsForUI(ComponentModel component)
			throws IOException {
		ObjectNode allComponentResultsNode = OBJECTMAPPER.createObjectNode();
		ArrayNode arrayNode = allComponentResultsNode.arrayNode();
		List<ComponentResult> componentResultList = ComponentResult
				.findAllByComponent(component);
		for (ComponentResult componentResult : componentResultList) {
			ObjectNode componentResultNode = componentResultAsJsonNode(componentResult);
			arrayNode.add(componentResultNode);
		}
		allComponentResultsNode.put(DATA, arrayNode);
		String asJsonStr = OBJECTMAPPER
				.writeValueAsString(allComponentResultsNode);
		return asJsonStr;
	}

	private static ObjectNode studyResultAsJsonNode(StudyResult studyResult)
			throws IOException {
		ObjectNode studyResultNode = OBJECTMAPPER.valueToTree(studyResult);

		// Add study's ID and title
		studyResultNode.put("studyId", studyResult.getStudy().getId());
		studyResultNode.put("studyTitle", studyResult.getStudy().getTitle());

		// Add all componentResults
		ArrayNode arrayNode = studyResultNode.arrayNode();
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			ObjectNode componentResultNode = componentResultAsJsonNode(componentResult);
			arrayNode.add(componentResultNode);
		}
		studyResultNode.put("componentResults", arrayNode);

		return studyResultNode;
	}

	private static ObjectNode componentResultAsJsonNode(
			ComponentResult componentResult) throws IOException {
		ObjectNode componentResultNode = OBJECTMAPPER
				.valueToTree(componentResult);

		// Add studyId and componentId
		componentResultNode.put("studyId", componentResult.getComponent()
				.getStudy().getId());
		componentResultNode.put("componentId", componentResult.getComponent()
				.getId());
		componentResultNode.put("componentTitle", componentResult
				.getComponent().getTitle());

		// Add componentResult's data
		componentResultNode
				.put(DATA, componentResultDataForUI(componentResult));

		return componentResultNode;
	}

	public static String studyForUI(StudyModel study)
			throws JsonProcessingException {
		ObjectNode studyNode = OBJECTMAPPER.valueToTree(study);
		studyNode.put("resultCount", StudyResult.countByStudy(study));
		String asJsonStr = OBJECTMAPPER.writeValueAsString(studyNode);
		return asJsonStr;
	}

	public static String allComponentsForUI(List<ComponentModel> componentList)
			throws JsonProcessingException {
		ArrayNode arrayNode = OBJECTMAPPER.createArrayNode();
		for (ComponentModel component : componentList) {
			ObjectNode componentNode = OBJECTMAPPER.valueToTree(component);
			// Add count of component's results
			componentNode.put("resultCount",
					ComponentResult.countByComponent(component));
			arrayNode.add(componentNode);
		}
		ObjectNode componentsNode = OBJECTMAPPER.createObjectNode();
		componentsNode.put(DATA, arrayNode);
		String asJsonStr = OBJECTMAPPER.writeValueAsString(componentsNode);
		return asJsonStr;
	}

	public static String allWorkersForUI(Set<Worker> workerSet)
			throws JsonProcessingException {
		ArrayNode arrayNode = OBJECTMAPPER.createArrayNode();
		for (Worker worker : workerSet) {
			ObjectNode workerNode = OBJECTMAPPER.valueToTree(worker);
			arrayNode.add(workerNode);
		}
		ObjectNode workersNode = OBJECTMAPPER.createObjectNode();
		workersNode.put(DATA, arrayNode);
		String asJsonStr = OBJECTMAPPER.writeValueAsString(workersNode);
		return asJsonStr;
	}

	public static String asJson(Object obj) throws JsonProcessingException {
		ObjectWriter objectWriter = OBJECTMAPPER.writer();
		String objectAsJson = objectWriter.writeValueAsString(obj);
		return objectAsJson;
	}

	/**
	 * Marshals the given object into JSON, adds the application's version, and
	 * returns it as String. It uses the view JsonForIO.
	 */
	public static String asJsonForIO(Object obj) throws IOException {
		ObjectNode node = generateNodeWithVersionForIO(obj);
		return OBJECTMAPPER.writer().writeValueAsString(node);
	}

	/**
	 * Marshals the given object into JSON, adds the application's version, and
	 * saves it into the given File. It uses the view JsonForIO.
	 */
	public static void asJsonForIO(Object obj, File file) throws IOException {
		ObjectNode node = generateNodeWithVersionForIO(obj);
		OBJECTMAPPER.writer().writeValue(file, node);
	}

	private static ObjectNode generateNodeWithVersionForIO(Object obj)
			throws IOException {
		ObjectNode node = OBJECTMAPPER.createObjectNode();
		node.put(
				VERSION,
				Play.application().configuration()
						.getString("application.version"));
		// Unnecessary conversion into a temporary string - better solution?
		String objAsJson = OBJECTMAPPER.writerWithView(JsonForIO.class)
				.writeValueAsString(obj);
		node.put(DATA, OBJECTMAPPER.readTree(objAsJson));
		return node;
	}

	/**
	 * Accepts an JSON String and turns the data object within this JSON String
	 * into an object of the given type.
	 */
	public static <T> T unmarshallingIO(String jsonStr, Class<T> modelClass)
			throws JsonParseException, JsonMappingException, IOException {
		JsonNode node = OBJECTMAPPER.readTree(jsonStr).findValue(DATA);
		T object = OBJECTMAPPER.treeToValue(node, modelClass);
		return object;
	}

	/**
	 * Unmarshalling of an JSON string without throwing an exception. Instead
	 * error message and Exception are stored within the instance.
	 * 
	 * @author Kristian Lange
	 */
	public static class UploadUnmarshaller {

		private String errorMsg;
		private Exception exception;

		public String getErrorMsg() {
			return errorMsg;
		}

		public Exception getException() {
			return exception;
		}

		public <T> T unmarshalling(File file, Class<T> modelClass) {
			T object = null;
			String jsonStr = null;
			try {
				// Don't unmarshall file directly so we can create error
				// messages.
				jsonStr = IOUtils.readFile(file);
			} catch (IOException e) {
				errorMsg = ErrorMessages.COULDNT_READ_FILE;
				exception = e;
				return null;
			}
			try {
				object = JsonUtils.unmarshallingIO(jsonStr, modelClass);
			} catch (IOException e) {
				errorMsg = ErrorMessages.COULDNT_READ_JSON;
				exception = e;
				return null;
			}
			return object;
		}
	}

}
