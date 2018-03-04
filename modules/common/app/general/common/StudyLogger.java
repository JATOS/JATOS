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
 * @author Kristian Lange
 * result data: ok
 * should worker get a UUID too?: no
 * log batch: ok
 * server data MAC, time: ok
 * study created by user: ok
 * ImportExport.exportDataOfComponentResults why not call prepareResponseForExport(): ok
 * overwriteing existing study: no: ok
 * batch created: ok
 * locked/unlocked: ok
 * study started with worker type ID in batch: ok
 * study finished (time): ok
 * properties field names that changed: no: ok
 * remove study -> keep log file (mention in GUI) -> rename old log file (+ timestamp): ok
 * export result data with hash as file name: ok
 * GUI: download button for whole study log as raw JSON: ok
 * GUI: add show study log: ok
 * remove hashes: ok
 * detect log file deletion: ok
 * add IP to first log line: no: ok
 * <p>
 * make log path configurable via prod.conf: ok
 * retire filename wrong: ok
 * In log after delete of study: no hashes and no UUIDs: ok
 * GUI: show warn if log file not found: ok
 * GUI: What if log file deleted and recreated: show warn message: no: ok
 * application/x-download needed?: no: ok
 * maybe use logfilereader?
 * GUI: show only 10000? lines, if more show warn: ok
 * GUI: show last 1000 lines, reversed, as raw and pretty JSON: ok
 * GUI: show pretty and readable (date): ok
 * GUI: download via button: ok
 * log export file hashes?: ok
 * result file name: ok
 * GUI: in reverse order: ok
 * show Eli: ok
 * comments in studylogger, studies and beautify: ok
 * go through all msges
 * remove results in bulk is not efficient (ResultRemover): ok
 * download file not reversed and not chunked: ok
 * check componentService and studyServerice: ok
 * check LogFileReader: ok
 * check result removing: ok
 * name files: jatos-studylog-bla and jatos-results-bla: ok
 * what if log is corrupted: JATOS should still work: ok
 * resultcreator.createStudyResult: why not put worker into study?: ok
 * run old tests and fix them for service classes: ok
 * write tests: StudyLogger, Studies, HashUtils: ok
 * check Java docs again
 * docs: result data export file name jatos_results_
 */

/**
 * StudyLogger provides logging for JATOS studies. Each study gets it's own log usually created
 * while the study is created.
 *
 * Major events are written into this log:
 *   - study creation/deletion/recreation
 *   - batch creation/deletion
 *   - study run start/stops/aborts
 *   - result data storing/deletion
 *   - result data export
 *
 * Whenever the log entry handles result data a SHA-256 hash of the data is included in the log. If
 * it exports files a SHA-256 hash of the content of the file is included in the log.
 */
@Singleton
public class StudyLogger {

    private static final Logger.ALogger LOGGER = Logger.of(StudyLogger.class);

    public static final String SHA_256 = "SHA-256";

    public static final String TIMESTAMP = "timestamp";
    public static final String MSG = "msg";
    public static final String STUDY_UUID = "studyUuid";
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
        String initialMsg = "Initial entry";
        create(study, initialMsg);
    }

    private void recreate(Study study) {
        String initialMsg = "Could not find a study log although the study already exists. " +
                "Create a new one.";
        create(study, initialMsg);
    }

    private void create(Study study, String msg) {
        Path studyLogPath = Paths.get(getPath(study));
        try {
            Path studyLogDirPath = Paths.get(Common.getStudyLogsPath());
            if (!Files.isDirectory(studyLogDirPath)) {
                Files.createDirectories(studyLogDirPath);
            }
            if (Files.exists(studyLogPath)) {
                LOGGER.error("A study log with " + studyLogPath + " exists already.");
            }

            ObjectNode jsonObj = Json.newObject();
            jsonObj.put(TIMESTAMP, Instant.now().toEpochMilli());
            jsonObj.put(MSG, msg);
            jsonObj.put(STUDY_UUID, study.getUuid());
            jsonObj.put(JATOS_VERSION, Common.getJatosVersion());
            jsonObj.put(SERVERS_MAC, Common.getMac());
            jsonObj.put(HASH_FUNCTION, SHA_256);
            String logEntry = "\n" + Json.mapper().writer().writeValueAsString(jsonObj);
            byte[] logEntryInBytes = logEntry.getBytes(StandardCharsets.ISO_8859_1);
            Files.write(studyLogPath, logEntryInBytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            LOGGER.error("Study log couldn't be created: " + studyLogPath, e);
        }
    }

    public void retire(Study study) {
        log(study, "Last entry of the study log", Pair.of("studyUuid", study.getUuid()));
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
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        log(study, jsonObj);
    }

    public void log(Study study, String msg, Pair<String, Object> additionalInfo) {
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(additionalInfo.getKey(), additionalInfo.getValue().toString());
        log(study, jsonObj);
    }

    public void log(Study study, String msg, Worker worker) {
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(WORKER_ID, worker.getId());
        log(study, jsonObj);
    }

    public void log(Study study, String msg, Batch batch) {
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(BATCH_ID, batch.getId());
        log(study, jsonObj);
    }

    public void log(Study study, String msg, Batch batch, Worker worker) {
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
     */
    public void logStudyResultDataExporting(List<StudyResult> studyResultList,
            String exportedResultDataStr) {
        List<ComponentResult> componentResultList = studyResultList.stream()
                .map(StudyResult::getComponentResultList).flatMap(List::stream)
                .collect(Collectors.toList());
        logComponentResultDataExporting(componentResultList, exportedResultDataStr);
    }

    /**
     * Adds an entry to the study log: exporting of several ComponentResults. Adds the hashes of
     * all result data, the file hash ( hash of the whole string that is to be exported), and
     * all worker IDs.
     */
    public void logComponentResultDataExporting(List<ComponentResult> componentResultList,
            String exportedResultDataStr) {
        if (componentResultList.isEmpty()) {
            return;
        }

        StudyResult studyResult = componentResultList.get(0).getStudyResult();
        ArrayNode dataHashesArray = Json.newArray();
        ArrayNode componentUuidArray = Json.newArray();
        ArrayNode workerIdArray = Json.newArray();
        for (ComponentResult cr : componentResultList) {
            String resultDataHash = (cr.getData() != null) ?
                    HashUtils.getHash(cr.getData(), SHA_256) : NO_DATA;
            dataHashesArray.add(resultDataHash);
            componentUuidArray.add(cr.getComponent().getUuid());
            workerIdArray.add(cr.getWorkerId());
        }
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, "Exported result data to a file. Hashes of each result data and the " +
                "hash of the whole file content are logged here.");
        jsonObj.put(FILE_HASH, HashUtils.getHash(exportedResultDataStr, SHA_256));
        jsonObj.set(DATA_HASHES, dataHashesArray);
        jsonObj.set(WORKER_IDS, workerIdArray);
        log(studyResult.getStudy(), jsonObj);
    }

    /**
     * Adds an entry to the study log: adds the hash of the result data, component UUID, and the
     * worker ID
     */
    public void logResultDataStoring(ComponentResult componentResult) {
        String resultDataHash = (componentResult.getData() != null) ?
                HashUtils.getHash(componentResult.getData(), SHA_256) : NO_DATA;
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
     * @param componentResultList array of ComponentResults to be removed
     */
    public void logResultDataRemoving(List<ComponentResult> componentResultList) {
        if (componentResultList.size() == 0) {
            return;
        }

        Study study = componentResultList.get(0).getStudyResult().getStudy();
        ArrayNode dataHashesArray = Json.newArray();
        ArrayNode componentUuidArray = Json.newArray();
        ArrayNode workerIdArray = Json.newArray();
        for (ComponentResult cr : componentResultList) {
            dataHashesArray.add((cr.getData() != null) ?
                    HashUtils.getHash(cr.getData(), SHA_256) : NO_DATA);
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
     * @param studyResultList List of StudyResults which component results should be removed
     */
    public void logStudyResultDataRemoving(List<StudyResult> studyResultList) {
        List<ComponentResult> componentResultList = studyResultList.stream()
                .map(StudyResult::getComponentResultList).flatMap(List::stream)
                .collect(Collectors.toList());
        logResultDataRemoving(componentResultList);
    }

    public void log(Study study, ObjectNode jsonObj) {
        Path studyLogPath = Paths.get(getPath(study));
        if (Files.notExists(studyLogPath)) {
            // Later we should increase this to 'error'
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
