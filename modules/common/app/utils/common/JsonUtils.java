package utils.common;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.inject.Singleton;

import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import models.common.Batch;
import models.common.Component;
import models.common.ComponentResult;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.Worker;
import play.Logger;
import utils.common.JsonUtils.SidebarStudy.SidebarComponent;

/**
 * Utility class the handles everything around JSON, like marshaling and
 * unmarshaling.
 * 
 * @author Kristian Lange
 */
@Singleton
public class JsonUtils {

	public static final String DATA = "data";
	public static final String VERSION = "version";
	private static final String CLASS_NAME = JsonUtils.class.getSimpleName();

	/**
	 * ObjectMapper from Jackson JSON library to marshal/unmarshal. It considers
	 * the default timezone.
	 */
	public static final ObjectMapper OBJECTMAPPER = new ObjectMapper()
			.setTimeZone(TimeZone.getDefault());

	/**
	 * Helper class for selectively marshaling an Object to JSON. Only fields of
	 * that Object that are annotated with this class will be serialised. The
	 * intended use is in the publix module (used for running a study).
	 */
	public static class JsonForPublix {
	}

	/**
	 * Helper class for selectively marshaling an Object to JSON. Only fields of
	 * that Object that are annotated with this class will be serialised.
	 * Intended use: import/export between different instances of JATOS.
	 */
	public static class JsonForIO {
	}

	/**
	 * Marshalling an Object into an JSON string. It only considers fields that
	 * are annotated with 'JsonForPublix'.
	 */
	public String asJsonForPublix(Object obj) throws JsonProcessingException {
		ObjectWriter objectWriter = OBJECTMAPPER
				.writerWithView(JsonForPublix.class);
		return objectWriter.writeValueAsString(obj);
	}

	/**
	 * Formats a JSON string into a standardised form suitable for storing into
	 * a DB.
	 */
	public static String asStringForDB(String jsonData) {
		if (jsonData == null) {
			return null;
		}
		if (!JsonUtils.isValidJSON(jsonData)) {
			// Set the invalid string anyway, but don't standardise it. It will
			// cause an error during next validate() if one tries to edit this
			// component.
			return jsonData;
		}
		String jsonDataForDB = null;
		try {
			jsonDataForDB = OBJECTMAPPER.readTree(jsonData).toString();
		} catch (Exception e) {
			Logger.info(CLASS_NAME
					+ ".asStringForDB: error probably due to invalid JSON");
		}
		return jsonDataForDB;
	}

	/**
	 * Checks whether the given string is a valid JSON string.
	 */
	public static boolean isValidJSON(final String jsonDataStr) {
		boolean valid = false;
		try {
			// Parse the string. If an exception occurs return false and true
			// otherwise.
			final JsonParser parser = OBJECTMAPPER.getFactory()
					.createParser(jsonDataStr);
			while (parser.nextToken() != null) {
			}
			valid = true;
		} catch (Exception e) {
			Logger.info(CLASS_NAME
					+ ".isValidJSON: error probably due to invalid JSON");
			valid = false;
		}
		return valid;
	}

	/**
	 * Returns init data that are requested during initialisation of each
	 * component run: Marshals the study properties and the component properties
	 * and puts them together with the session data (stored in StudyResult) into
	 * a new JSON object.
	 */
	public ObjectNode initData(Batch batch, StudyResult studyResult, Study study,
			Component component) throws IOException {
		String studyProperties = asJsonForPublix(study);
		String batchProperties = asJsonForPublix(batch);
		ArrayNode componentList = getComponentListForInitData(study);
		String componentProperties = asJsonForPublix(component);
		String studySessionData = studyResult.getStudySessionData();
		ObjectNode initData = OBJECTMAPPER.createObjectNode();
		initData.put("studySessionData", studySessionData);
		// This is ugly: first marshaling, now unmarshaling again
		initData.set("studyProperties", OBJECTMAPPER.readTree(studyProperties));
		initData.set("batchProperties", OBJECTMAPPER.readTree(batchProperties));
		initData.set("componentList", componentList);
		initData.set("componentProperties",
				OBJECTMAPPER.readTree(componentProperties));
		return initData;
	}

	/**
	 * Returns an JSON ArrayNode with with a component list intended for use in
	 * jatos.js initData. For each component it adds only the bare minimum of
	 * data.
	 */
	private ArrayNode getComponentListForInitData(Study study) {
		ArrayNode componentList = OBJECTMAPPER.createArrayNode();
		for (Component tempComponent : study.getComponentList()) {
			ObjectNode componentNode = OBJECTMAPPER.createObjectNode();
			componentNode.put("id", tempComponent.getId());
			componentNode.put("title", tempComponent.getTitle());
			componentNode.put("active", tempComponent.isActive());
			componentNode.put("reloadable", tempComponent.isReloadable());
			componentList.add(componentNode);
		}
		return componentList;
	}

	/**
	 * Returns the data string of a componentResult limited to
	 * MAX_CHAR_PER_RESULT characters.
	 */
	public String componentResultDataForUI(ComponentResult componentResult) {
		final int MAX_CHAR_PER_RESULT = 1000;
		String data = componentResult.getData();
		if (data != null) {
			// Escape HTML tags and &
			data = data.replace("&", "&amp").replace("<", "&lt;").replace(">",
					"&gt;");
			if (data.length() < MAX_CHAR_PER_RESULT) {
				return data;
			} else {
				return data.substring(0, MAX_CHAR_PER_RESULT) + " ...";
			}
		} else {
			return "none";
		}
	}

	/**
	 * Returns all studyResults as a JSON string. It's including the
	 * studyResult's componentResults.
	 */
	public String allStudyResultsForUI(List<StudyResult> studyResultList)
			throws JsonProcessingException {
		ObjectNode allStudyResultsNode = OBJECTMAPPER.createObjectNode();
		ArrayNode arrayNode = allStudyResultsNode.arrayNode();
		for (StudyResult studyResult : studyResultList) {
			ObjectNode studyResultNode = studyResultAsJsonNode(studyResult);
			arrayNode.add(studyResultNode);
		}
		allStudyResultsNode.set(DATA, arrayNode);
		return OBJECTMAPPER.writeValueAsString(allStudyResultsNode);
	}

	/**
	 * Returns JSON of all ComponentResuls of the specified component. The JSON
	 * string is intended for use in JATOS' GUI.
	 */
	public String allComponentResultsForUI(
			List<ComponentResult> componentResultList)
					throws JsonProcessingException {
		ObjectNode allComponentResultsNode = OBJECTMAPPER.createObjectNode();
		ArrayNode arrayNode = allComponentResultsNode.arrayNode();
		for (ComponentResult componentResult : componentResultList) {
			ObjectNode componentResultNode = componentResultAsJsonNode(
					componentResult);
			arrayNode.add(componentResultNode);
		}
		allComponentResultsNode.set(DATA, arrayNode);
		return OBJECTMAPPER.writeValueAsString(allComponentResultsNode);
	}

	/**
	 * Returns ObjectNode of the given StudyResult. It contains the worker,
	 * study's ID and title, and all ComponentResults.
	 */
	private ObjectNode studyResultAsJsonNode(StudyResult studyResult) {
		ObjectNode studyResultNode = OBJECTMAPPER.valueToTree(studyResult);

		// Add worker
		ObjectNode workerNode = OBJECTMAPPER
				.valueToTree(initializeAndUnproxy(studyResult.getWorker()));
		studyResultNode.set("worker", workerNode);

		// Add extra variables
		studyResultNode.put("studyId", studyResult.getStudy().getId());
		studyResultNode.put("studyTitle", studyResult.getStudy().getTitle());
		studyResultNode.put("duration", getDurationPretty(
				studyResult.getStartDate(), studyResult.getEndDate()));
		String groupResultId = studyResult.getGroupResult() != null
				? studyResult.getGroupResult().getId().toString() : null;
		studyResultNode.put("groupResultId", groupResultId);

		// Add all componentResults
		ArrayNode arrayNode = studyResultNode.arrayNode();
		for (ComponentResult componentResult : studyResult
				.getComponentResultList()) {
			ObjectNode componentResultNode = componentResultAsJsonNode(
					componentResult);
			arrayNode.add(componentResultNode);
		}
		studyResultNode.set("componentResults", arrayNode);

		return studyResultNode;
	}

	/**
	 * Returns an ObjectNode of the given ComponentResult. It contains the study
	 * ID, component ID and component title.
	 */
	private ObjectNode componentResultAsJsonNode(
			ComponentResult componentResult) {
		ObjectNode componentResultNode = OBJECTMAPPER
				.valueToTree(componentResult);

		// Add extra variables
		componentResultNode.put("studyId",
				componentResult.getComponent().getStudy().getId());
		componentResultNode.put("componentId",
				componentResult.getComponent().getId());
		componentResultNode.put("componentTitle",
				componentResult.getComponent().getTitle());
		componentResultNode.put("duration", getDurationPretty(
				componentResult.getStartDate(), componentResult.getEndDate()));
		GroupResult groupResult = componentResult.getStudyResult()
				.getGroupResult();
		String groupResultId = groupResult != null
				? groupResult.getId().toString() : null;
		componentResultNode.put("groupResultId", groupResultId);

		// Add componentResult's data
		componentResultNode.put(DATA,
				componentResultDataForUI(componentResult));

		return componentResultNode;
	}

	private static String getDurationPretty(Timestamp startDate,
			Timestamp endDate) {
		if (endDate != null) {
			long duration = endDate.getTime() - startDate.getTime();
			long diffSeconds = duration / 1000 % 60;
			long diffMinutes = duration / (60 * 1000) % 60;
			long diffHours = duration / (60 * 60 * 1000) % 24;
			long diffDays = duration / (24 * 60 * 60 * 1000);
			String asStr = String.format("%02d", diffHours) + ":"
					+ String.format("%02d", diffMinutes) + ":"
					+ String.format("%02d", diffSeconds);
			if (diffDays == 0) {
				return asStr;
			} else {
				return diffDays + ":" + asStr;
			}
		}
		return null;
	}

	/**
	 * Returns JSON string of the given study. This JSON is intended for JATOS'
	 * GUI.
	 */
	public String studyForUI(Study study, int resultCount)
			throws JsonProcessingException {
		ObjectNode studyNode = OBJECTMAPPER.valueToTree(study);
		studyNode.put("resultCount", resultCount);
		return OBJECTMAPPER.writeValueAsString(studyNode);
	}

	/**
	 * Returns JsonNode of the given user list for use in the change-user-form.
	 * This JSON is intended for JATOS' GUI.
	 */
	public JsonNode usersForStudyUI(List<User> userList, Study study) {
		ArrayNode userArrayNode = OBJECTMAPPER.createArrayNode();
		for (User user : userList) {
			ObjectNode userNode = OBJECTMAPPER.createObjectNode();
			userNode.put("name", user.getName());
			userNode.put("email", user.getEmail());
			// Is this user admin of the study - NOT is it the admin user
			userNode.put("admin", study.hasUser(user));
			userArrayNode.add(userNode);
		}
		return userArrayNode;
	}

	/**
	 * Returns the JSON data for the sidebar (study title, ID and components)
	 */
	public JsonNode sidebarStudyList(List<Study> studyList) {
		List<SidebarStudy> sidebarStudyList = new ArrayList<>();
		for (Study study : studyList) {
			SidebarStudy sidebarStudy = new SidebarStudy();
			sidebarStudy.id = study.getId();
			sidebarStudy.uuid = study.getUuid();
			sidebarStudy.title = study.getTitle();
			sidebarStudy.locked = study.isLocked();
			for (Component component : study.getComponentList()) {
				SidebarComponent sidebarComponent = new SidebarStudy.SidebarComponent();
				sidebarComponent.id = component.getId();
				sidebarComponent.uuid = component.getUuid();
				sidebarComponent.title = component.getTitle();
				sidebarStudy.componentList.add(sidebarComponent);
			}
			sidebarStudyList.add(sidebarStudy);
		}
		Collections.sort(sidebarStudyList, new SidebarStudyComparator());
		return asJsonNode(sidebarStudyList);
	}

	/**
	 * Comparator that compares to study's titles.
	 */
	private class SidebarStudyComparator implements Comparator<SidebarStudy> {
		@Override
		public int compare(SidebarStudy ss1, SidebarStudy ss2) {
			return ss1.title.toLowerCase().compareTo(ss2.title.toLowerCase());
		}
	}

	/**
	 * Little model class to store some study data for the UI's sidebar.
	 */
	static class SidebarStudy {
		public Long id;
		public String uuid;
		public String title;
		public boolean locked;
		public final List<SidebarComponent> componentList = new ArrayList<>();

		/**
		 * Little model class to store some component data for the UI's sidebar.
		 */
		static class SidebarComponent {
			public Long id;
			public String uuid;
			public String title;
		}
	}

	/**
	 * Returns a JSON string of all components in the given list. This includes
	 * the 'resultCount', the number of ComponentResults of this component so
	 * far. Intended for use in JATOS' GUI.
	 */
	public JsonNode allComponentsForUI(List<Component> componentList,
			List<Integer> resultCountList) {
		ArrayNode arrayNode = OBJECTMAPPER.createArrayNode();
		// int i = 1;
		for (int i = 0; i < componentList.size(); i++) {
			ObjectNode componentNode = OBJECTMAPPER
					.valueToTree(componentList.get(i));
			// Add count of component's results
			componentNode.put("resultCount", resultCountList.get(i));
			int position = i + 1;
			componentNode.put("position", position);
			arrayNode.add(componentNode);
		}
		ObjectNode componentsNode = OBJECTMAPPER.createObjectNode();
		componentsNode.set(DATA, arrayNode);
		return componentsNode;
	}

	/**
	 * Returns a JSON string with the given set of workers wrapped in a data
	 * object. Intended for use in JATOS' GUI / datatables plugin.
	 */
	public String allWorkersForUI(Set<Worker> workerSet)
			throws JsonProcessingException {
		ArrayNode arrayNode = OBJECTMAPPER.createArrayNode();
		for (Worker worker : workerSet) {
			ObjectNode workerNode = OBJECTMAPPER
					.valueToTree(initializeAndUnproxy(worker));
			// workerNode.put(Worker.UI_WORKER_TYPE, worker.getUIWorkerType());
			arrayNode.add(workerNode);
		}
		ObjectNode workersNode = OBJECTMAPPER.createObjectNode();
		workersNode.set(DATA, arrayNode);
		return OBJECTMAPPER.writeValueAsString(workersNode);
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

	/**
	 * Generic JSON marshaler.
	 */
	public static String asJson(Object obj) {
		ObjectWriter objectWriter = OBJECTMAPPER.writer();
		String objectAsJson = null;
		try {
			objectAsJson = objectWriter.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			Logger.error(CLASS_NAME + ".asJson: error marshalling object");
		}
		return objectAsJson;
	}

	/**
	 * Generic JSON marshaler.
	 */
	public static JsonNode asJsonNode(Object obj) {
		return OBJECTMAPPER.valueToTree(obj);
	}

	/**
	 * Marshals the given object into JSON, adds the application's version, and
	 * returns it as String. It uses the view JsonForIO.
	 */
	public String componentAsJsonForIO(Object obj) throws IOException {
		ObjectNode node = generateNodeWithVersionForIO(obj,
				String.valueOf(Component.SERIAL_VERSION));
		return OBJECTMAPPER.writer().writeValueAsString(node);
	}

	/**
	 * Marshals the given object into JSON, adds the application's version, and
	 * saves it into the given File. It uses the view JsonForIO.
	 */
	public void studyAsJsonForIO(Study study, File file) throws IOException {
		ObjectNode node = generateNodeWithVersionForIO(study,
				String.valueOf(Study.SERIAL_VERSION));
		OBJECTMAPPER.writer().writeValue(file, node);
	}

	/**
	 * Generic JSON marshaler that adds the JATOS version to the JSON string.
	 * Intended for file IO.
	 */
	private ObjectNode generateNodeWithVersionForIO(Object obj, String version)
			throws IOException {
		ObjectNode node = OBJECTMAPPER.createObjectNode();
		node.put(VERSION, version);
		// Unnecessary conversion into a temporary string - better solution?
		String objAsJson = OBJECTMAPPER.writerWithView(JsonForIO.class)
				.writeValueAsString(obj);
		node.set(DATA, OBJECTMAPPER.readTree(objAsJson));
		return node;
	}

}
