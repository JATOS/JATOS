package general.common;

import com.diffplug.common.base.Errors;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.common.*;
import models.common.workers.Worker;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.StreamConverters;
import org.apache.pekko.util.ByteString;
import play.Logger;
import play.libs.Json;
import utils.common.HashUtils;
import utils.common.IOUtils;

import javax.inject.Singleton;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * StudyLogger provides logging for JATOS studies. Each study gets it's own log usually created while the study is
 * created.
 *
 * Major events are written into this log: - study creation/deletion/recreation - batch creation/deletion - study run
 * start/stops/aborts - result data storing - NOT logging any user adding/removing
 *
 * Whenever the log entry handles result data, an SHA-256 hash of the data is included in the log. If it exports files,
 * an SHA-256 hash of the content of the file is included in the log.
 *
 * The log uses charset ISO_8859_1.
 */
@Singleton
public class StudyLogger {

    private static final Logger.ALogger LOGGER = Logger.of(StudyLogger.class);

    private static final Pattern NON_ISO_8859_1 = Pattern.compile("[^\\u0000-\\u00FF]");

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
    private static final String STUDYCODE = "studyCode";
    private static final String BATCH_ID = "batchId";
    private static final String DATA_HASH = "dataHash";
    private static final String FILE_HASH = "fileHash";
    private static final String FILE_NAME = "fileName";
    private static final String NO_DATA = "no data";
    private static final String COMPONENT_UUID = "componentUuid";

    public String getFilename(String studyUuid) {
        return studyUuid + ".log";
    }

    public String getPath(String studyUuid) {
        return Common.getStudyLogsPath() + File.separator + studyUuid + ".log";
    }

    public String getRetiredFilename(String studyUuid) {
        return studyUuid + "_" + Instant.now().toEpochMilli() + ".retired";
    }

    public String getRetiredPath(String studyUuid) {
        return Common.getStudyLogsPath() + File.separator + getRetiredFilename(studyUuid);
    }

    public void create(String studyUuid) {
        create(studyUuid, "Initial entry");
    }

    private void recreate(String studyUuid) {
        String initialMsg = "Could not find a study log although the study already exists. Create a new one.";
        create(studyUuid, initialMsg);
    }

    private void create(String studyUuid, String msg) {
        if (!Common.isStudyLogsEnabled()) return;
        Path studyLogPath = Path.of(getPath(studyUuid));
        try {
            if (Files.exists(studyLogPath)) {
                LOGGER.error("A study log with " + studyLogPath + " exists already.");
                retire(studyUuid);
            }

            ObjectNode jsonObj = Json.newObject();
            jsonObj.put(MSG, msg);
            jsonObj.put(TIMESTAMP, Instant.now().toEpochMilli());
            jsonObj.put(STUDY_UUID, studyUuid);
            jsonObj.put(SERVERS_MAC, Common.getMac());
            jsonObj.put(HASH_FUNCTION, HashUtils.SHA_256);
            String logEntry = "\n" + Json.mapper().writer().writeValueAsString(jsonObj);
            String sanitizedLogEntry = sanitizeForIso88591(logEntry);
            Files.writeString(studyLogPath, sanitizedLogEntry, StandardCharsets.ISO_8859_1, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            LOGGER.error("Study log couldn't be created: " + studyLogPath, e);
        }
    }

    public String retire(String studyUuid) {
        if (!Common.isStudyLogsEnabled()) return null;
        log(studyUuid, null, "Last entry of the study log", Pair.of(STUDY_UUID, studyUuid));
        Path logPath = Path.of(getPath(studyUuid));
        Path retiredLogPath = Path.of(getRetiredPath(studyUuid));
        if (Files.exists(logPath)) {
            try {
                IOUtils.moveFile(logPath, retiredLogPath, false);
            } catch (IOException e) {
                LOGGER.error("Study log couldn't be moved from " + logPath + " to " + retiredLogPath, e);
            }
        }
        return retiredLogPath.getFileName().toString();
    }

    public void log(String studyUuid, String userName, String msg) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode entry = Json.newObject();
        entry.put(MSG, msg);
        log(studyUuid, userName, entry);
    }

    public void log(String studyUuid, String userName, String msg, Long batchId) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode entry = Json.newObject();
        entry.put(MSG, msg);
        entry.put(BATCH_ID, batchId);
        log(studyUuid, userName, entry);
    }

    public void log(String studyUuid, String userName, String msg, Pair<String, Object> additionalInfo) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(additionalInfo.getKey(), String.valueOf(additionalInfo.getValue()));
        log(studyUuid, userName, jsonObj);
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

    public void log(StudyLink studyLink, String msg, Worker worker) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, msg);
        jsonObj.put(BATCH_ID, studyLink.getBatch().getId());
        jsonObj.put(STUDYCODE, studyLink.getStudyCode());
        jsonObj.put(WORKER_ID, worker.getId());
        log(studyLink.getBatch().getStudy(), null, jsonObj);
    }

    /**
     * Adds an entry to the study log: adds the hash of the file, component UUID, and the worker ID
     *
     * @param file File that will be stored
     */
    public void logResultUploading(Path file, ComponentResult componentResult) {
        if (!Common.isStudyLogsEnabled()) return;
        if (file == null) return;
        StudyResult studyResult = componentResult.getStudyResult();
        String fileHash = HashUtils.getHash(file, HashUtils.SHA_256);

        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, "Uploaded file");
        jsonObj.put(COMPONENT_UUID, componentResult.getComponent().getUuid());
        jsonObj.put(WORKER_ID, componentResult.getWorkerId());
        jsonObj.put(FILE_HASH, fileHash);
        jsonObj.put(FILE_NAME, file.getFileName().toString());
        log(studyResult.getStudy(), null, jsonObj);
    }

    /**
     * Adds an entry to the study log: adds the hash of the result data, component UUID, and the worker ID
     *
     * @param componentResult ComponentResults that will be stored
     */
    public void logResultDataStoring(ComponentResult componentResult, String data, boolean append) {
        if (!Common.isStudyLogsEnabled()) return;
        if (componentResult == null) return;

        StudyResult studyResult = componentResult.getStudyResult();
        String resultDataHash = (data != null) ? HashUtils.getHash(data, HashUtils.SHA_256) : NO_DATA;
        ObjectNode jsonObj = Json.newObject();
        jsonObj.put(MSG, append ? "Appended component result data" : "Replaced component result data");
        jsonObj.put(COMPONENT_UUID, componentResult.getComponent().getUuid());
        jsonObj.put(WORKER_ID, componentResult.getWorkerId());
        jsonObj.put(DATA_HASH, resultDataHash);
        log(studyResult.getStudy(), null, jsonObj);
    }

    public void logStudyDescriptionHash(Study study, User user) {
        if (!Common.isStudyLogsEnabled()) return;
        logStudyDescriptionHash(study.getUuid(), user != null ? user.getName() : null, study.getDescriptionHash());
    }

    public void logStudyDescriptionHash(String studyUuid, String userName, String descriptionHash) {
        if (!Common.isStudyLogsEnabled()) return;
        ObjectNode entry = Json.newObject();
        entry.put(MSG, "Study description changed");
        entry.put(STUDY_DESCRIPTION_HASH, String.valueOf(descriptionHash));
        log(studyUuid, userName, entry);
    }

    private void log(Study study, User user, ObjectNode jsonObj) {
        log(study.getUuid(), user != null ? user.getName() : null, jsonObj);
    }

    /**
     * Adds the given jsonObj as an entry to the study
     */
    private void log(String studyUuid, String userName, ObjectNode jsonObj) {
        if (!Common.isStudyLogsEnabled()) return;
        Path studyLogPath = Path.of(getPath(studyUuid));
        if (Files.notExists(studyLogPath)) {
            LOGGER.info("Couldn't find log for study with UUID " + studyUuid + " in " + studyLogPath
                    + ". Create new log file.");
            recreate(studyUuid);
        }
        try {
            if (userName != null) jsonObj.put(USER_NAME, userName);
            jsonObj.put(TIMESTAMP, Instant.now().toEpochMilli());
            String logEntry = "\n" + Json.mapper().writer().writeValueAsString(jsonObj);
            String sanitizedLogEntry = sanitizeForIso88591(logEntry);
            Files.writeString(studyLogPath, sanitizedLogEntry, StandardCharsets.ISO_8859_1, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Study log couldn't be written: " + studyLogPath, e);
        }
    }

    /**
     * @param study      the study of which log will be read
     * @param entryLimit number of max entries will be read from the log
     */
    public Source<ByteString, ?> readLogFile(Study study, int entryLimit) {
        return StreamConverters.asOutputStream()
                .keepAlive(Duration.ofSeconds(30), () -> ByteString.fromString(" "))
                .mapMaterializedValue(outputStream -> CompletableFuture.runAsync(() -> {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream));
                    Errors.rethrow().run(() -> streamLogFile(writer, getPath(study.getUuid()), entryLimit));
                    Errors.rethrow().run(writer::flush);
                    Errors.rethrow().run(writer::close);
                }));
    }

    private void streamLogFile(Writer writer, String filePath, int lineLimit) throws IOException {
        writer.write("[");
        try (ReversedLinesFileReader reader = ReversedLinesFileReader.builder()
                .setPath(filePath)
                .setCharset(StandardCharsets.ISO_8859_1)
                .get()) {
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
                writer.write(currentLine);
            }
            if (nextLine != null) {
                writer.write(",\"" + "--- Log is cut here. Download it to get the whole file. ---" + "\"");
            }
        } catch (Exception e) {
            writer.write("\"" + "Error: Could not open log file" + "\"");
            LOGGER.error("Couldn't open study log " + filePath);
        } finally {
            writer.write("]");
        }
    }

    private boolean hasNextLine(String nextLine, int lineLimit, int lineNumber) {
        return nextLine != null && (lineLimit == -1 || lineNumber <= lineLimit);
    }

    public String sanitizeForIso88591(String str) {
        if (str == null) return null;
        return NON_ISO_8859_1.matcher(str).replaceAll("?");
    }

}
