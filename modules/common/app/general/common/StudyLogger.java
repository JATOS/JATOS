package general.common;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.common.Batch;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.Worker;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang3.tuple.Pair;
import play.Logger;
import play.libs.Json;
import utils.common.HashUtils;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * StudyLogger provides logging for JATOS studies. Each study gets it's own log usually created
 * while the study is created.
 * <p>
 * Major events are written into this log:
 * - study creation/deletion/recreation
 * - batch creation/deletion
 * - study run start/stops/aborts
 * - result data storing/deletion
 * - result data export
 * - NOT logging any user adding/removing
 * <p>
 * Whenever the log entry handles result data a SHA-256 hash of the data is included in the log. If
 * it exports files a SHA-256 hash of the content of the file is included in the log.
 *
 * @author Kristian Lange 2018
 */
@Singleton
public class StudyLogger {

    private static final Logger.ALogger LOGGER = Logger.of(StudyLogger.class);

    /**
     * JSON key names used in study log
     */
    public static final String TIMESTAMP = "timestamp";
    public static final String MSG = "msg";
    public static final String STUDY_UUID = "studyUuid";
    public static final String STUDY_DESCRIPTION_HASH = "studyDescriptionHash";
    public static final String JATOS_VERSION = "jatosVersion";
    public static final String SERVERS_MAC = "serversMac";
    public static final String HASH_FUNCTION = "hashFunction";
    public static final String WORKER_ID = "workerId";
    public static final String WORKER_IDS = "workerIds";
    public static final String BATCH_ID = "batchId";
    public static final String DATA_HASH = "dataHash";
    public static final String DATA_HASHES = "dataHashes";
    public static final String NO_DATA = "no data";
    public static final String FILE_HASH = "fileHash";
    public static final String COMPONENT_UUID = "componentUuid";
    public static final String COMPONENT_UUIDS = "componentUuids";

    public String getFilename(Study study) {
        return study.getUuid() + ".log";
    }

    public String getPath(Study study) {
        return Common.getStudyLogsPath() + File.separator + getFilename(study);
    }

    public String getRetiredFilename(Study study) {
        return study.getUuid() + "_" + Instant.now().toEpochMilli() + ".retired";
    }

    public String getRetiredPath(Study study) {
        return Common.getStudyLogsPath() + File.separator + getRetiredFilename(study);
    }

    public void create(Study study) {
        if (!Common.isStudyLogsEnabled()) return;
        String initialMsg = "Initial entry";
        create(study, initialMsg);
    }

    private void recreate(Study study) {
        if (!Common.isStudyLogsEnabled()) return;
        String initialMsg = "Could not find a study log although the study already exists. " +
                "Create a new one.";
        create(study, initialMsg);
    }

    private void create(Study study, String msg) {
        if (!Common.isStudyLogsEnabled()) return;
        Path studyLogPath = Paths.get(getPath(study));
        try {
            Path studyLogDirPath = Paths.get(Common.getStudyLogsPath());
            if (!Files.isDirectory(studyLogDirPath)) {
                Files.createDirectories(studyLogDirPath);
            }
            if (Files.exists(studyLogPath)) {
                LOGGER.error("A study log with " + studyLogPath + " exists already.");
                retire(study);
            }

            ObjectNode jsonObj = Json.newObject();
            jsonObj.put(TIMESTAMP, Instant.now().toEpochMilli());
            jsonObj.put(MSG, msg);
            jsonObj.put(STUDY_UUID, study.getUuid());
            jsonObj.put(JATOS_VERSION, Common.getJatosVersion());
            jsonObj.put(SERVERS_MAC, Common.getMac());
            jsonObj.put(HASH_FUNCTION, HashUtils.SHA_256);
            String logEntry = "\n" + Json.mapper().writer().writeValueAsString(jsonObj);
            byte[] logEntryInBytes = logEntry.getBytes(StandardCharsets.ISO_8859_1);
            Files.write(studyLogPath, logEntryInBytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            LOGGER.error("Study log couldn't be created: " + studyLogPath, e);
        }
    }

    public void retire(Study study) {
        if (!Common.isStudyLogsEnabled()) return;
        log(study, "Last entry of the study log", Pair.of(STUDY_UUID, study.getUuid()));
        Path logPath = Paths.get(getPath(study));
        Path retiredLogPath = Paths.get(getRetiredPath(study));
        if (Files.exists(logPath)) {
            try {
                Files.move(logPath, retiredLogPath);
            } catch (IOException e) {
                LOGGER.error("Study log couldn't be moved from " + logPath + " to " +
                        retiredLogPath, e);
            }
        }
    }

    public void log(Study study, String msg) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        log(study, jsonObj);
    }

    public void log(Study study, String msg, Pair<String, Object> additionalInfo) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(additionalInfo.getKey(), String.valueOf(additionalInfo.getValue()));
        log(study, jsonObj);
    }

    public void log(Study study, String msg, Worker worker) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(WORKER_ID, worker.getId());
        log(study, jsonObj);
    }

    public void log(Study study, String msg, Batch batch) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(BATCH_ID, batch.getId());
        log(study, jsonObj);
    }

    public void log(Study study, String msg, Batch batch, Worker worker) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(BATCH_ID, batch.getId());
        jsonObj.put(WORKER_ID, worker.getId());
        log(study, jsonObj);
    }

    /**
     * Adds an entry to the study log: exporting of several StudyResults. Adds the hashes of
     * all result data, the file hash ( hash of the whole string that is to be exported), and
     * all worker IDs.
     *
     * @param studyResultList list of StudyResults that will be exported
     */
    public void logStudyResultDataExporting(List<StudyResult> studyResultList,
            String exportedResultDataStr) {
        if (!Common.isStudyLogsEnabled()) return;
        List<ComponentResult> componentResultList = studyResultList.stream()
                .map(StudyResult::getComponentResultList).flatMap(List::stream)
                .collect(Collectors.toList());
        logComponentResultDataExporting(componentResultList, exportedResultDataStr);
    }

    /**
     * Adds an entry to the study log: exporting of several ComponentResults. Adds the hashes of
     * all result data, the file hash (hash of the whole file text that is to be exported), and
     * all worker IDs.
     *
     * @param componentResultList list of ComponentResults that will be exported
     */
    public void logComponentResultDataExporting(List<ComponentResult> componentResultList,
            String exportedResultDataStr) {
        if (!Common.isStudyLogsEnabled()) return;
        if (componentResultList.isEmpty()) return;

        StudyResult studyResult = componentResultList.get(0).getStudyResult();
        ArrayNode dataHashesArray = Json.newArray();
        ArrayNode componentUuidArray = Json.newArray();
        ArrayNode workerIdArray = Json.newArray();
        for (ComponentResult cr : componentResultList) {
            String resultDataHash = (cr.getData() != null) ?
                    HashUtils.getHash(cr.getData(), HashUtils.SHA_256) : NO_DATA;
            dataHashesArray.add(resultDataHash);
            componentUuidArray.add(cr.getComponent().getUuid());
            workerIdArray.add(cr.getWorkerId());
        }
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, "Exported result data to a file. Hashes of each result data and the " +
                "hash of the whole file content are logged here.");
        jsonObj.put(FILE_HASH, HashUtils.getHash(exportedResultDataStr, HashUtils.SHA_256));
        jsonObj.set(DATA_HASHES, dataHashesArray);
        jsonObj.set(WORKER_IDS, workerIdArray);
        log(studyResult.getStudy(), jsonObj);
    }

    /**
     * Adds an entry to the study log: adds the hash of the result data, component UUID, and the
     * worker ID
     *
     * @param componentResult ComponentResults that will be stored
     */
    public void logResultDataStoring(ComponentResult componentResult) {
        if (!Common.isStudyLogsEnabled()) return;
        String resultDataHash = (componentResult.getData() != null) ?
                HashUtils.getHash(componentResult.getData(), HashUtils.SHA_256) : NO_DATA;
        Study study = componentResult.getStudyResult().getStudy();
        String componentUuid = componentResult.getComponent().getUuid();
        Long workerId = componentResult.getWorkerId();
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, "Stored component result data");
        jsonObj.put(COMPONENT_UUID, componentUuid);
        jsonObj.put(WORKER_ID, workerId);
        jsonObj.put(DATA_HASH, resultDataHash);
        log(study, jsonObj);
    }

    /**
     * Adds an entry to the study log: adds hashes of all component result data, all component UUIDs,
     * and all worker IDs of the worker who run this component. All component results must come
     * from the same study but not necessarily from the same study result.
     *
     * @param componentResultList list of ComponentResults that will be removed
     */
    public void logResultDataRemoving(List<ComponentResult> componentResultList) {
        if (!Common.isStudyLogsEnabled()) return;
        if (componentResultList.size() == 0) return;

        Study study = componentResultList.get(0).getStudyResult().getStudy();
        ArrayNode dataHashesArray = Json.newArray();
        ArrayNode componentUuidArray = Json.newArray();
        ArrayNode workerIdArray = Json.newArray();
        for (ComponentResult cr : componentResultList) {
            dataHashesArray.add((cr.getData() != null) ?
                    HashUtils.getHash(cr.getData(), HashUtils.SHA_256) : NO_DATA);
            componentUuidArray.add(cr.getComponent().getUuid());
            workerIdArray.add(cr.getWorkerId());
        }
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, "Removed component result data");
        jsonObj.set(COMPONENT_UUIDS, componentUuidArray);
        jsonObj.set(WORKER_IDS, workerIdArray);
        jsonObj.set(DATA_HASHES, dataHashesArray);
        log(study, jsonObj);
    }

    /**
     * Adds an entry to the study log: adds hashes of all component result data, all component UUIDs,
     * and all worker IDs of the worker who run this study.
     *
     * @param studyResultList List of StudyResults which will be removed
     */
    public void logStudyResultDataRemoving(List<StudyResult> studyResultList) {
        if (!Common.isStudyLogsEnabled()) return;
        List<ComponentResult> componentResultList = studyResultList.stream()
                .map(StudyResult::getComponentResultList).flatMap(List::stream)
                .collect(Collectors.toList());
        logResultDataRemoving(componentResultList);
    }

    public void logStudyDescriptionHash(Study study) {
        log(study, "Study description changed",
                Pair.of(STUDY_DESCRIPTION_HASH, study.getDescriptionHash()));
    }

    /**
     * Adds the given jsonObj as an entry to the study
     */
    public void log(Study study, ObjectNode jsonObj) {
        if (!Common.isStudyLogsEnabled()) return;
        Path studyLogPath = Paths.get(getPath(study));
        if (Files.notExists(studyLogPath)) {
            LOGGER.info("Couldn't find log for study with UUID " + study.getUuid() + " in " +
                    studyLogPath + ". Create new log file.");
            recreate(study);
        }
        try {
            jsonObj.put(TIMESTAMP, Instant.now().toEpochMilli());
            String logEntry = "\n" + Json.mapper().writer().writeValueAsString(jsonObj);
            byte[] logEntryInBytes = logEntry.getBytes(StandardCharsets.ISO_8859_1);
            Files.write(studyLogPath, logEntryInBytes, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Study log couldn't be written: " + studyLogPath, e);
        }
    }

    /**
     * @param study      the study of which log will be read
     * @param entryLimit number of max entries will be read from the log
     */
    public Source<ByteString, ?> readLogFile(Study study, int entryLimit) {
        // Prepare a chunked text stream (I have no idea what I'm doing here -
        // https://www.playframework.com/documentation/2.5.x/JavaStream)
        int bufferSize = entryLimit > 256 ? entryLimit : 256; // ensure min buffer size
        return Source.<ByteString>actorRef(bufferSize, OverflowStrategy.fail())
                .mapMaterializedValue(sourceActor ->
                        fillSourceWithLogFile(sourceActor, getPath(study), entryLimit));
    }

    private Object fillSourceWithLogFile(ActorRef sourceActor, String filePath, int lineLimit) {
        File logFile = new File(filePath);
        sourceActor.tell(ByteString.fromString("["), null);
        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(logFile)) {
            String nextLine = reader.readLine();
            int lineNumber = 1;
            while (hasNextLine(nextLine, lineLimit, lineNumber)) {
                lineNumber++;
                String currentLine = nextLine;
                nextLine = reader.readLine();
                if (currentLine.trim().isEmpty()) {
                    // Apparently chunked responses can't handle empty lines
                    continue;
                }
                if (hasNextLine(nextLine, lineLimit, lineNumber)) {
                    currentLine += ",";
                }
                sourceActor.tell(ByteString.fromString(currentLine), null);
            }
            if (nextLine != null) {
                sourceActor.tell(
                        ByteString.fromString(",\"" + MessagesStrings.LOG_CUT + "\""), null);
            }
        } catch (Exception e) {
            sourceActor.tell(
                    ByteString.fromString("\"" + MessagesStrings.COULDNT_OPEN_LOG + "\""), null);
            LOGGER.error("Couldn't open study log " + filePath);
        } finally {
            sourceActor.tell(ByteString.fromString("]"), null);
            sourceActor.tell(new Status.Success(NotUsed.getInstance()), null);
        }
        return NotUsed.getInstance();
    }

    private boolean hasNextLine(String nextLine, int lineLimit, int lineNumber) {
        return nextLine != null && (lineLimit == -1 || lineNumber <= lineLimit);
    }

}
