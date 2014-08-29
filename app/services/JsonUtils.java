package services;

import java.util.TimeZone;

import models.results.ComponentResult;
import models.results.StudyResult;
import models.workers.Worker;

import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;

import play.Logger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import controllers.publix.Publix;

public class JsonUtils {

	private static final String CLASS_NAME = Publix.class.getSimpleName();

	/**
	 * Helper class for selectively JSON-serialise an Object. Only fields of
	 * that Object that are annotated with this class will be serialised. The
	 * intended use is in the public API.
	 */
	public static class JsonForPublix {
	}

	/**
	 * Helper class for selectively JSON-serialise an Object. Only fields of
	 * that Object that are annotated with this class will be serialised. The
	 * intended use is in the MechArg.
	 */
	public static class JsonForMA extends JsonForPublix {
	}

	/**
	 * Turns a JSON string into a 'pretty' formatted JSON string suitable for
	 * presentation in a UI. The JSON itself (semantics) aren't changed. If the
	 * JSON string isn't valid it returns null.
	 */
	public static String makePretty(String jsonData) {
		String jsonDataPretty = null;
		try {
			ObjectMapper mapper = new ObjectMapper();
			Object json = mapper.readValue(jsonData, Object.class);
			jsonDataPretty = mapper.writerWithDefaultPrettyPrinter()
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
			ObjectMapper mapper = new ObjectMapper();
			String jsonData = mapper.readTree(jsonDataStr).toString();
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
			final JsonParser parser = new ObjectMapper().getFactory()
					.createParser(jsonDataStr);
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
	 * Serializes an Object into an JSON string. It only considers fields that
	 * are annotated with 'JsonForPublix'.
	 * 
	 * @throws JsonProcessingException
	 */
	public static String asJsonForPublix(Object obj)
			throws JsonProcessingException {
		ObjectWriter objectWriter = new ObjectMapper()
				.writerWithView(JsonForPublix.class);
		String componentAsJson = objectWriter.writeValueAsString(obj);
		return componentAsJson;
	}

	/**
	 * Serializes an Object into an JSON string. It considers the default
	 * timezone.
	 * 
	 * @throws JsonProcessingException
	 */
	public static String asJsonForMA(Object obj) throws JsonProcessingException {
		ObjectWriter objectWriter = new ObjectMapper()
				.setTimeZone(TimeZone.getDefault())
				.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
				.writerWithView(JsonForMA.class);
		String resultAsJson = objectWriter.writeValueAsString(obj);
		return resultAsJson;
	}

	/**
	 * Serializes a ComponentResult into an JSON string. It considers the
	 * default timezone.
	 * 
	 * @throws JsonProcessingException
	 */
	public static String componentResultAsJsonForMA(
			ComponentResult componentResult) throws JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper().setTimeZone(
				TimeZone.getDefault()).configure(
				SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		ObjectNode componentResultNode = objectMapper
				.valueToTree(componentResult);

		// Add studyId and componentId
		componentResultNode.put("studyId", componentResult.getComponent()
				.getStudy().getId());
		componentResultNode.put("componentId", componentResult.getComponent()
				.getId());

		// Add worker
		StudyResult studyResult = componentResult.getStudyResult();
		Worker worker = initializeAndUnproxy(studyResult.getWorker());
		ObjectNode workerNode = objectMapper.valueToTree(worker);
		componentResultNode.with("worker").putAll(workerNode);

		// Write as string
		String resultAsJson = objectMapper
				.writeValueAsString(componentResultNode);

		// Add componentResult's data to the end
		resultAsJson = resultAsJson.substring(0, resultAsJson.length() - 1);
		resultAsJson = resultAsJson + ",\"data\":" + componentResult.getData()
				+ "}";

		return resultAsJson;
	}

	@SuppressWarnings("unchecked")
	public static <T> T initializeAndUnproxy(T obj) {
		Hibernate.initialize(obj);
		if (obj instanceof HibernateProxy) {
			obj = (T) ((HibernateProxy) obj).getHibernateLazyInitializer()
					.getImplementation();
		}
		return obj;
	}

}
