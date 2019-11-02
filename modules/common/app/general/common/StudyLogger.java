package general.common;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.common.*;
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

/**
 * StudyLogger provides logging for JATOS studies. Each study gets it's own log usually created while the study is
 * created.
 * <p>
 * Major events are written into this log: - study creation/deletion/recreation - batch creation/deletion - study run
 * start/stops/aborts - result data storing - NOT logging any user adding/removing
 * <p>
 * Whenever the log entry handles result data a SHA-256 hash of the data is included in the log. If it exports files a
 * SHA-256 hash of the content of the file is included in the log.
 * <p>
 * The log uses charset ISO_8859_1.
 *
 * @author Kristian Lange
 */
@Singleton
public class StudyLogger {

    private static final Logger.ALogger LOGGER = Logger.of(StudyLogger.class);

    /**
     * JSON key names used in study log
     */
    private static final String TIMESTAMP = "timestamp";
    private static final String MSG = "msg";
    private static final String STUDY_UUID = "studyUuid";
    private static final String STUDY_DESCRIPTION_HASH = "studyDescriptionHash";
    private static final String SERVERS_MAC = "serversMac";
    private static final String HASH_FUNCTION = "hashFunction";
    private static final String USER_NAME = "userName";
    private static final String WORKER_ID = "workerId";
    private static final String BATCH_ID = "batchId";
    private static final String DATA_HASH = "dataHash";
    private static final String NO_DATA = "no data";
    private static final String COMPONENT_UUID = "componentUuid";

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
        String initialMsg = "Could not find a study log although the study already exists. Create a new one.";
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
            jsonObj.put(MSG, msg);
            jsonObj.put(TIMESTAMP, Instant.now().toEpochMilli());
            jsonObj.put(STUDY_UUID, study.getUuid());
            jsonObj.put(SERVERS_MAC, Common.getMac());
            jsonObj.put(HASH_FUNCTION, HashUtils.SHA_256);
            String logEntry = "\n" + Json.mapper().writer().writeValueAsString(jsonObj);
            byte[] logEntryInBytes = logEntry.getBytes(StandardCharsets.ISO_8859_1);
            Files.write(studyLogPath, logEntryInBytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            LOGGER.error("Study log couldn't be created: " + studyLogPath, e);
        }
    }

    public String retire(Study study) {
        if (!Common.isStudyLogsEnabled()) return null;
        log(study, null, "Last entry of the study log", Pair.of(STUDY_UUID, study.getUuid()));
        Path logPath = Paths.get(getPath(study));
        Path retiredLogPath = Paths.get(getRetiredPath(study));
        if (Files.exists(logPath)) {
            try {
                Files.move(logPath, retiredLogPath);
            } catch (IOException e) {
                LOGGER.error("Study log couldn't be moved from " + logPath + " to " + retiredLogPath, e);
            }
        }
        return retiredLogPath.getFileName().toString();
    }

    public void log(Study study, User user, String msg) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        log(study, user, jsonObj);
    }

    public void log(Study study, User user, String msg, Batch batch) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(BATCH_ID, batch.getId());
        log(study, user, jsonObj);
    }

    public void log(Study study, User user, String msg, Pair<String, Object> additionalInfo) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(additionalInfo.getKey(), String.valueOf(additionalInfo.getValue()));
        log(study, user, jsonObj);
    }

    public void log(Study study, String msg, Worker worker) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(WORKER_ID, worker.getId());
        log(study, null, jsonObj);
    }

    public void log(Study study, String msg, Batch batch, Worker worker) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(BATCH_ID, batch.getId());
        jsonObj.put(WORKER_ID, worker.getId());
        log(study, null, jsonObj);
    }

    /**
     * Adds an entry to the study log: adds the hash of the result data, component UUID, and the worker ID
     *
     * @param componentResult ComponentResults that will be stored
     */
    public void logResultDataStoring(ComponentResult componentResult) {
        if (!Common.isStudyLogsEnabled()) return;
        if (componentResult == null) return;

        StudyResult studyResult = componentResult.getStudyResult();
        String resultDataHash = (componentResult.getData() != null) ? HashUtils.getHash(componentResult.getData(),
                HashUtils.SHA_256) : NO_DATA;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, "Stored component result data");
        jsonObj.put(COMPONENT_UUID, componentResult.getComponent().getUuid());
        jsonObj.put(WORKER_ID, componentResult.getWorkerId());
        jsonObj.put(DATA_HASH, resultDataHash);
        log(studyResult.getStudy(), null, jsonObj);
    }

    public void logStudyDescriptionHash(Study study, User user) {
        log(study, user, "Study description changed", Pair.of(STUDY_DESCRIPTION_HASH, study.getDescriptionHash()));
    }

    /**
     * Adds the given jsonObj as an entry to the study
     */
    private void log(Study study, User user, ObjectNode jsonObj) {
        if (!Common.isStudyLogsEnabled()) return;
        Path studyLogPath = Paths.get(getPath(study));
        if (Files.notExists(studyLogPath)) {
            LOGGER.info("Couldn't find log for study with UUID " + study.getUuid() + " in " + studyLogPath
                    + ". Create new log file.");
            recreate(study);
        }
        try {
            if (user != null) jsonObj.put(USER_NAME, user.getName());
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
        return Source.<ByteString>actorRef(bufferSize, OverflowStrategy.fail()).mapMaterializedValue(
                sourceActor -> fillSourceWithLogFile(sourceActor, getPath(study), entryLimit));
    }

    private Object fillSourceWithLogFile(ActorRef sourceActor, String filePath, int lineLimit) {
        File logFile = new File(filePath);
        sourceActor.tell(ByteString.fromString("["), null);
        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(logFile, StandardCharsets.ISO_8859_1)) {
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
                sourceActor.tell(ByteString.fromString(",\"" + MessagesStrings.LOG_CUT + "\""), null);
            }
        } catch (Exception e) {
            sourceActor.tell(ByteString.fromString("\"" + MessagesStrings.COULDNT_OPEN_LOG + "\""), null);
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
