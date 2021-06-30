package utils.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import models.common.*;
import models.common.workers.JatosWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import play.Logger;
import play.Logger.ALogger;
import play.libs.Json;
import utils.common.JsonUtils.SidebarStudy.SidebarComponent;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class the handles everything around JSON, like marshaling and
 * unmarshaling. Uses a custom Json JSON object mapper defined in
 * {@link JsonObjectMapper}.
 *
 * @author Kristian Lange
 */
@Singleton
public class JsonUtils {

    private static final ALogger LOGGER = Logger.of(JsonUtils.class);

    public static final String DATA = "data";
    public static final String VERSION = "version";

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
        ObjectWriter objectWriter =
                Json.mapper().writerWithView(JsonForPublix.class);
        return objectWriter.writeValueAsString(obj);
    }

    /**
     * Formats a JSON string into a minimized form suitable for storing into a
     * DB.
     */
    public static String asStringForDB(String jsonData) {
        if (Strings.isNullOrEmpty(jsonData)) {
            return null;
        }
        if (!JsonUtils.isValid(jsonData)) {
            // Set the invalid string anyway, but don't standardize it. It will
            // cause an error during next validation.
            return jsonData;
        }
        String jsonDataForDB = null;
        try {
            jsonDataForDB = Json.mapper().readTree(jsonData).toString();
        } catch (Exception e) {
            LOGGER.info(".asStringForDB: error probably due to invalid JSON");
        }
        return jsonDataForDB;
    }

    /**
     * Checks whether the given string is a valid JSON string.
     */
    public static boolean isValid(final String json) {
        boolean valid = false;
        try {
            Json.mapper().readTree(json);
            valid = true;
        } catch (IOException e) {
            LOGGER.info(".isValid: error probably due to invalid JSON");
        }
        return valid;
    }

    /**
     * Returns init data that are requested during initialisation of each
     * component run: Marshals the study properties and the component properties
     * and puts them together with the session data (stored in StudyResult) into
     * a new JSON object.
     */
    public JsonNode initData(Batch batch, StudyResult studyResult, Study study, Component component)
            throws IOException {
        String studyProperties = asJsonForPublix(study);
        String batchProperties = asJsonForPublix(batch);
        ArrayNode componentList = getComponentListForInitData(study);
        String componentProperties = asJsonForPublix(component);
        String studySessionData = studyResult.getStudySessionData();
        String urlQueryParameters = studyResult.getUrlQueryParameters();
        String studyLinkId = studyResult.getStudyLinkId();

        ObjectNode initData = Json.mapper().createObjectNode();
        initData.put("studySessionData", studySessionData);
        initData.set("studyProperties", Json.mapper().readTree(studyProperties));
        initData.set("batchProperties", Json.mapper().readTree(batchProperties));
        initData.set("componentList", componentList);
        initData.set("componentProperties", Json.mapper().readTree(componentProperties));
        initData.put("urlQueryParameters", urlQueryParameters);
        initData.put("studyLinkId", studyLinkId);
        return initData;
    }

    /**
     * Returns an JSON ArrayNode with with a component list intended for use in
     * jatos.js initData. For each component it adds only the bare minimum of
     * data.
     */
    private ArrayNode getComponentListForInitData(Study study) {
        ArrayNode componentList = Json.mapper().createArrayNode();
        for (Component component : study.getComponentList()) {
            ObjectNode componentNode = Json.mapper().createObjectNode();
            componentNode.put("id", component.getId());
            componentNode.put("uuid", component.getUuid());
            componentNode.put("title", component.getTitle());
            componentNode.put("active", component.isActive());
            componentNode.put("reloadable", component.isReloadable());
            componentNode.put("position", study.getComponentPosition(component));
            componentList.add(componentNode);
        }
        return componentList;
    }

    /**
     * Returns all GroupResults as a JSON string intended for GUI.
     */
    public JsonNode allGroupResultsForUI(List<GroupResult> groupResultList) {
        ObjectNode allGroupResultsNode = Json.newObject();
        ArrayNode arrayNode = allGroupResultsNode.arrayNode();
        for (GroupResult groupResult : groupResultList) {
            ObjectNode groupResultNode = Json.mapper().valueToTree(groupResult);

            // Add active workers
            ArrayNode activeWorkerIdListNode = groupResultNode.arrayNode();
            groupResult.getActiveMemberList().forEach(sr -> activeWorkerIdListNode.add(sr.getWorkerId()));
            groupResultNode.set("activeWorkerList", activeWorkerIdListNode);

            // Add history workers
            ArrayNode historyWorkerIdListNode = groupResultNode.arrayNode();
            groupResult.getHistoryMemberList().forEach(sr -> historyWorkerIdListNode.add(sr.getWorkerId()));
            groupResultNode.set("historyWorkerList", historyWorkerIdListNode);

            // Add study result count
            int resultCount = groupResult.getActiveMemberCount() + groupResult.getHistoryMemberCount();
            groupResultNode.put("resultCount", resultCount);

            arrayNode.add(groupResultNode);
        }
        allGroupResultsNode.set(DATA, arrayNode);
        return allGroupResultsNode;
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
            data = data.replace("&", "&amp").replace("<", "&lt;").replace(">", "&gt;");
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
     * Returns ObjectNode of the given StudyResult. It contains the worker,
     * study's ID and title, and all ComponentResults.
     */
    public JsonNode studyResultAsJsonNode(StudyResult studyResult, int componentResultCount) {
        ObjectNode studyResultNode = Json.mapper().valueToTree(studyResult);

        // Add worker
        ObjectNode workerNode = Json.mapper().valueToTree(initializeAndUnproxy(studyResult.getWorker()));
        studyResultNode.set("worker", workerNode);

        // Add extra variables
        studyResultNode.put("studyId", studyResult.getStudy().getId());
        studyResultNode.put("studyLinkId", studyResult.getStudyLinkId());
        studyResultNode.put("studyTitle", studyResult.getStudy().getTitle());
        studyResultNode.put("batchTitle", studyResult.getBatch().getTitle());
        String duration;
        if (studyResult.getEndDate() != null) {
            duration = getDurationPretty(studyResult.getStartDate(), studyResult.getEndDate());
        } else {
            duration = getDurationPretty(studyResult.getStartDate(), studyResult.getLastSeenDate());
            duration = duration != null ? duration + " (not finished yet)" : "none";
        }
        studyResultNode.put("duration", duration);
        studyResultNode.put("groupResultId", getGroupResultId(studyResult));
        studyResultNode.put("componentResultCount", componentResultCount);
        studyResultNode.put("hasResultFiles", hasResultUploadFiles(studyResult));

        return studyResultNode;
    }

    public JsonNode getComponentResultsByStudyResult(StudyResult studyResult) {
        ArrayNode componentResultsNode = Json.mapper().createArrayNode();
        for (ComponentResult componentResult : studyResult.getComponentResultList()) {
            JsonNode componentResultNode = componentResultAsJsonNode(componentResult, true);
            componentResultsNode.add(componentResultNode);
        }
        return componentResultsNode;
    }

    /**
     * Returns group result ID of the given StudyResult or null if it doesn't exist.
     * Get group result Id either from active or history group result.
     */
    private String getGroupResultId(StudyResult studyResult) {
        if (studyResult.getActiveGroupResult() != null) {
            return studyResult.getActiveGroupResult().getId().toString();
        } else if (studyResult.getHistoryGroupResult() != null) {
            return studyResult.getHistoryGroupResult().getId().toString();
        } else {
            return null;
        }
    }

    /**
     * Returns an ObjectNode of the given ComponentResult.
     */
    public JsonNode componentResultAsJsonNode(ComponentResult componentResult, boolean withData) {
        ObjectNode componentResultNode = Json.mapper().valueToTree(componentResult);

        // Add extra variables
        componentResultNode.put("studyId", componentResult.getComponent().getStudy().getId());
        componentResultNode.put("componentId", componentResult.getComponent().getId());
        componentResultNode.put("componentTitle", componentResult.getComponent().getTitle());
        componentResultNode.put("duration", getDurationPretty(
                componentResult.getStartDate(), componentResult.getEndDate()));
        componentResultNode.put("studyResultId", componentResult.getStudyResult().getId());
        componentResultNode.put("studyLinkId", componentResult.getStudyResult().getStudyLinkId());
        String groupResultId = getGroupResultId(componentResult.getStudyResult());
        componentResultNode.put("groupResultId", groupResultId);
        componentResultNode.put("batchTitle", componentResult.getStudyResult().getBatch().getTitle());

        // Add componentResult's data
        if (withData) {
            componentResultNode.put("data", componentResultDataForUI(componentResult));
        }
        int dataSize = componentResult.getData() != null ?
                componentResult.getData().getBytes(StandardCharsets.UTF_8).length : 0;
        componentResultNode.put("dataSize", Helpers.humanReadableByteCount(dataSize));

        // Add uploaded result files
        ArrayNode filesNode = componentResultNode.arrayNode();
        getResultUploadFiles(componentResult).forEach(filesNode::add);
        componentResultNode.set("files", filesNode);

        return componentResultNode;
    }

    private List<ObjectNode> getResultUploadFiles(ComponentResult componentResult) {
        Path dir = Paths.get(
                IOUtils.getResultUploadsDir(componentResult.getStudyResult().getId(), componentResult.getId()));
        if (Files.isDirectory(dir)) {
            try {
                return Files.list(dir).map(this::getResultUploadFileNode).collect(Collectors.toList());
            } catch (IOException e) {
                LOGGER.warn("Cannot open directory " + dir);
            }
        }
        return new ArrayList<>();
    }

    private ObjectNode getResultUploadFileNode(Path filePath) {
        String fileSize = null;
        try {
            FileChannel fileChannel = FileChannel.open(filePath);
            fileSize = Helpers.humanReadableByteCount(fileChannel.size());
        } catch (IOException e) {
            LOGGER.warn("Cannot open file " + filePath);
        }
        return Json.mapper().createObjectNode()
                .put("name", filePath.getFileName().toString())
                .put("size", fileSize);
    }

    private boolean hasResultUploadFiles(StudyResult studyResult) {
        Path dir = Paths.get(IOUtils.getResultUploadsDir(studyResult.getId()));
        if (Files.isDirectory(dir)) {
            try (Stream<Path> entries = Files.list(dir)) {
                return entries.findFirst().isPresent();
            } catch (IOException e) {
                LOGGER.warn("Cannot open directory " + dir);
            }
        }
        return false;
    }

    private static String getDurationPretty(Timestamp startDate, Timestamp endDate) {
        if (endDate == null) return null;
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

    /**
     * Returns JsonNode with all users of this study. This JSON is intended for
     * JATOS' GUI / in the change user modal.
     */
    public JsonNode memberUserArrayOfStudy(List<User> userList, Study study) {
        ArrayNode userArrayNode = Json.mapper().createArrayNode();
        for (User user : userList) {
            if (study.hasUser(user)) {
                ObjectNode userNode = memberUserOfStudy(user, study);
                userArrayNode.add(userNode);
            }
        }
        ObjectNode userDataNode = Json.mapper().createObjectNode();
        userDataNode.set(DATA, userArrayNode);
        return userDataNode;
    }

    /**
     * Returns JsonNode with the given user. This JSON is intended for JATOS'
     * GUI / in the change user modal.
     */
    public ObjectNode memberUserOfStudy(User user, Study study) {
        ObjectNode userNode = Json.mapper().createObjectNode();
        userNode.put("name", user.getName());
        userNode.put("username", user.getUsername());
        userNode.put("isMember", study.hasUser(user));
        return userNode;
    }

    /**
     * Returns a JsonNode with the usual user fields plus from all studies where
     * the user is member of the title and the number of members.
     */
    public JsonNode userData(User user) {
        ObjectNode userNode = Json.mapper().createObjectNode();
        userNode.put("name", user.getName());
        userNode.put("username", user.getUsername());
        ArrayNode roleListArray = Json.mapper().valueToTree(user.getRoleList());
        userNode.putArray("roleList").addAll(roleListArray);
        userNode.put("authMethod", user.getAuthMethod().name());
        // Add array with study titles
        ArrayNode studyArrayNode = Json.mapper().createArrayNode();
        for (Study study : user.getStudyList()) {
            ObjectNode studyNode = Json.mapper().createObjectNode();
            studyNode.put("id", study.getId());
            studyNode.put("title", study.getTitle());
            studyNode.put("userSize", study.getUserList().size());
            studyArrayNode.add(studyNode);
        }
        userNode.putArray("studyList").addAll(studyArrayNode);
        return userNode;
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
                SidebarComponent sidebarComponent =
                        new SidebarStudy.SidebarComponent();
                sidebarComponent.id = component.getId();
                sidebarComponent.uuid = component.getUuid();
                sidebarComponent.title = component.getTitle();
                sidebarStudy.componentList.add(sidebarComponent);
            }
            sidebarStudyList.add(sidebarStudy);
        }
        sidebarStudyList.sort(new SidebarStudyComparator());
        return asJsonNode(sidebarStudyList);
    }

    /**
     * Comparator that compares to study's titles.
     */
    private static class SidebarStudyComparator implements Comparator<SidebarStudy> {
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
     * Returns a JSON string of all batches that belong to the given study. This includes the
     * 'resultCount' (the number of StudyResults of this batch so far), 'workerCount' (number of
     * workers without JatosWorkers), and the 'groupCount' (number of GroupResults of this batch
     * so far). Intended for use in JATOS' GUI.
     */
    public JsonNode allBatchesByStudyForUI(List<Batch> batchList, List<Integer> resultCountList,
            List<Integer> groupCountList) {
        ArrayNode batchListNode = Json.mapper().createArrayNode();
        for (int i = 0; i < batchList.size(); i++) {
            ObjectNode batchNode = getBatchByStudyForUI(batchList.get(i), resultCountList.get(i),
                    groupCountList.get(i));
            int position = i + 1;
            batchNode.put("position", position);
            batchListNode.add(batchNode);
        }
        ObjectNode dataNode = Json.mapper().createObjectNode();
        dataNode.set("data", batchListNode);
        return dataNode;
    }

    /**
     * Returns a JSON string of one batch. This includes the 'resultCount' (the number of
     * StudyResults of this batch so far), 'workerCount' (number of workers without JatosWorkers),
     * and the 'groupCount' (number of GroupResults of this batch so far).
     * Intended for use in JATOS' GUI.
     */
    public ObjectNode getBatchByStudyForUI(Batch batch, Integer resultCount, Integer groupCount) {
        ObjectNode batchNode = Json.mapper().valueToTree(batch);
        // Set allowed worker types
        batchNode.set("allowedWorkerTypes", asJsonNode(batch.getAllowedWorkerTypes()));
        // Add count of batch's study results
        batchNode.put("resultCount", resultCount);
        // Add count of batch's workers (without JatosWorker)
        batchNode.put("workerCount", batch.getWorkerList().size());
        // Add count of batch's group results
        batchNode.put("groupCount", groupCount);
        return batchNode;
    }

    /**
     * Returns a JSON string of all components in the given list. This includes
     * the 'resultCount', the number of ComponentResults of this component so
     * far. Intended for use in JATOS' GUI.
     */
    public JsonNode allComponentsForUI(List<Component> componentList,
            List<Integer> resultCountList) {
        ArrayNode arrayNode = Json.mapper().createArrayNode();
        // int i = 1;
        for (int i = 0; i < componentList.size(); i++) {
            ObjectNode componentNode = Json.mapper()
                    .valueToTree(componentList.get(i));
            // Add count of component's results
            componentNode.put("resultCount", resultCountList.get(i));
            int position = i + 1;
            componentNode.put("position", position);
            arrayNode.add(componentNode);
        }
        ObjectNode componentsNode = Json.mapper().createObjectNode();
        componentsNode.set(DATA, arrayNode);
        return componentsNode;
    }

    /**
     * Returns a JSON string with the given set of workers wrapped in a data
     * object. Intended for use in JATOS' GUI.
     */
    public JsonNode workersForTableData(Set<Worker> workerSet, Study study) {
        ArrayNode workerArrayNode = Json.mapper().createArrayNode();
        for (Worker worker : workerSet) {
            ObjectNode workerNode = Json.mapper().valueToTree(initializeAndUnproxy(worker));

            List<Batch> batchList = worker.getBatchList().stream()
                    .filter(b -> study.getBatchList().contains(b))
                    .collect(Collectors.toList());
            workerNode.set("batchList", Json.mapper().valueToTree(batchList));

            Optional<StudyResult> last = worker.getLastStudyResult();
            String lastStudyState = last.map(studyResult -> studyResult.getStudyState().name()).orElse(null);
            workerNode.put("lastStudyState", lastStudyState);

            addUsernameForJatosWorker(worker, workerNode);
            workerArrayNode.add(workerNode);
        }
        ObjectNode workersNode = Json.mapper().createObjectNode();
        workersNode.set(DATA, workerArrayNode);
        return workersNode;
    }

    public JsonNode studyLinksSetupData(Batch batch, Map<String, Integer> studyResultCountsPerWorker,
            Long personalSingleLinkCount, Long personalMultipleLinkCount) {
        ObjectNode studyLinkSetupData = Json.mapper().createObjectNode();
        studyLinkSetupData.set("studyResultCountsPerWorker", asJsonNode(studyResultCountsPerWorker));
        studyLinkSetupData.put("personalSingleLinkCount", personalSingleLinkCount);
        studyLinkSetupData.put("personalMultipleLinkCount", personalMultipleLinkCount);
        studyLinkSetupData.set("allowedWorkerTypes", asJsonNode(batch.getAllowedWorkerTypes()));
        return studyLinkSetupData;
    }

    public JsonNode studyLinksData(List<StudyLink> studyLinkList) {
        ObjectNode dataNode = Json.mapper().createObjectNode();
        ArrayNode arrayNode = Json.mapper().createArrayNode();

        for (StudyLink studyLink : studyLinkList) {
            ObjectNode studyLinkDataNode = Json.mapper().createObjectNode();
            studyLinkDataNode.put("studyLinkId", studyLink.getId());
            studyLinkDataNode.put("active", studyLink.isActive());

            if (studyLink.getWorker() != null) {
                Worker worker = studyLink.getWorker();

                studyLinkDataNode.put("workerId", worker.getId());

                // Worker's comment
                String comment;
                switch (worker.getWorkerType()) {
                    case PersonalSingleWorker.WORKER_TYPE:
                        comment = ((PersonalSingleWorker) worker).getComment();
                        break;
                    case PersonalMultipleWorker.WORKER_TYPE:
                        comment = ((PersonalMultipleWorker) worker).getComment();
                        break;
                    default:
                        throw new IllegalArgumentException(
                                ".studyLinkData: illegal workerType " + worker.getWorkerType());
                }
                studyLinkDataNode.put("comment", !StringUtils.isBlank(comment) ? comment : "none");

                // (last) StudyResult's state
                Optional<StudyResult> lastStudyResult = worker.getLastStudyResult();
                if (lastStudyResult.isPresent()) {
                    studyLinkDataNode.put("studyResultState", lastStudyResult.get().getStudyState().name());
                } else {
                    studyLinkDataNode.put("studyResultState", "none yet");
                }
            }
            arrayNode.add(studyLinkDataNode);
        }
        dataNode.set("data", arrayNode);
        return dataNode;
    }

    private void addUsernameForJatosWorker(Worker worker, ObjectNode workerNode) {
        if (worker instanceof JatosWorker) {
            JatosWorker jatosWorker = (JatosWorker) worker;
            if (jatosWorker.getUser() != null) {
                workerNode.put("username", jatosWorker.getUser().getUsername());
            } else {
                workerNode.put("username", "unknown");
            }
        } else if (worker.getWorkerType().equals(JatosWorker.WORKER_TYPE)) {
            // In case the JatosWorker's user is already removed from the
            // database Hibernate doesn't use the type JatosWorker
            workerNode.put("username", "unknown (probably deleted)");
        }
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
        ObjectWriter objectWriter = Json.mapper().writer();
        String objectAsJson = null;
        try {
            objectAsJson = objectWriter.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            LOGGER.error(".asJson: error marshalling object");
        }
        return objectAsJson;
    }

    /**
     * Generic JSON marshaler.
     */
    public JsonNode asJsonNode(Object obj) {
        return Json.mapper().valueToTree(obj);
    }

    /**
     * Marshals the given component into JSON, adds the current component serial
     * version, and returns it as JsonNode. It uses the view JsonForIO.
     */
    public JsonNode componentAsJsonForIO(Component component) throws IOException {
        ObjectNode componentNode = (ObjectNode) asObjectNodeWithIOView(component);
        return wrapNodeWithVersion(componentNode, String.valueOf(Component.SERIAL_VERSION));
    }

    /**
     * Marshals the given study into JSON, adds the current study serial
     * version, and saves it into the given File. It uses the view JsonForIO.
     */
    public void studyAsJsonForIO(Study study, File file) throws IOException {
        ObjectNode studyNode = (ObjectNode) asObjectNodeWithIOView(study);

        // Add components
        ArrayNode componentArray = (ArrayNode) asObjectNodeWithIOView(study.getComponentList());
        studyNode.putArray("componentList").addAll(componentArray);

        // Add default Batch
        ArrayNode batchArray = (ArrayNode) asObjectNodeWithIOView(study.getDefaultBatchList());
        studyNode.putArray("batchList").addAll(batchArray);

        // Add Study version
        JsonNode nodeForIO = wrapNodeWithVersion(studyNode, String.valueOf(Study.SERIAL_VERSION));

        // Write to file
        Json.mapper().writeValue(file, nodeForIO);
    }

    /**
     * Reads the given object into a JsonNode while using the JsonForIO view.
     */
    private JsonNode asObjectNodeWithIOView(Object obj) throws IOException {
        // Unnecessary conversion into a temporary string - better solution?
        String tmpStr = Json.mapper().writerWithView(JsonForIO.class)
                .writeValueAsString(obj);
        return Json.mapper().readTree(tmpStr);
    }

    private JsonNode wrapNodeWithVersion(JsonNode jsonNode, String version) {
        ObjectNode node = Json.mapper().createObjectNode();
        node.put(VERSION, version);
        node.set(DATA, jsonNode);
        return node;
    }

}
