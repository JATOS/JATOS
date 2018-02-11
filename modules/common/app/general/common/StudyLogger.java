package general.common;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import org.apache.commons.io.input.ReversedLinesFileReader;
import play.Logger;
import play.libs.Json;
import utils.common.HashUtils;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Kristian Lange
 * result data: ok
 * should worker get a UUID too?: no
 * log batch: ok
 * server data MAC, time: ok
 * study created by user: ok
 * ImportExport.exportDataOfComponentResults why not call prepareResponseForExport()
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
 * remove results in bulk is not efficient (ResultRemover)
 * make log path configurable via prod.conf: ok
 * retire filename wrong: ok
 * In log after delete of study: no hashes and no UUIDs: ok
 * maybe use logfilereader?
 * GUI: show warn if log file not found
 * GUI: What if log file deleted and recreated: show warn message
 * GUI: show only 10000? lines, if more show warn
 * GUI: show last 1000 lines, reversed, as raw and pretty JSON
 * GUI: show pretty and readable (date)
 * GUI: download via button
 * comments in studylogger, studies and beautify
 */
@Singleton
public class StudyLogger {

    private static final Logger.ALogger LOGGER = Logger.of(StudyLogger.class);

    public String getStudyLogFilename(Study study) {
        return study.getUuid() + ".log";
    }

    private String getStudyLogPath(Study study) {
        return Common.getStudyLogsPath() + File.separator + getStudyLogFilename(study);
    }

    public void log(StudyResult studyResult, String msg) {
        log(studyResult.getStudy(), msg, null, null);
    }

    public void log(Study study, String msg) {
        log(study, msg, null, null);
    }

    public void log(Study study, String msg, String[] resultDataHashes, String fileHash) {
        Path studyLogPath = Paths.get(getStudyLogPath(study));
        if (!Files.exists(studyLogPath)) {
            LOGGER.warn("Couldn't find log for study with UUID " + study.getUuid()
                    + " in " + studyLogPath + ". Maybe it was deleted? Create new log file.");
            recreateLog(study);
        }

        try {
            String logLine = "\n" + nextLogLine(msg, resultDataHashes, fileHash);
            Files.write(studyLogPath, logLine.getBytes(StandardCharsets.ISO_8859_1),
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Study log couldn't be written: " + studyLogPath, e);
        }
    }

    public void recreateLog(Study study) {
        String initialMsg = "The old log of study with UUID " + study.getUuid()
                + " seems to have been deleted and a new log was created"
                + " (JATOS version " + Common.getJatosVersion() + ", "
                + "JATOS server's MAC address " + getMAC() + ")";
        createLog(study, initialMsg);
    }

    public void createLog(Study study) {
        String initialMsg = "First line in log of study with UUID " + study.getUuid()
                + " (JATOS version " + Common.getJatosVersion() + ", "
                + "JATOS server's MAC address " + getMAC() + ")";
        createLog(study, initialMsg);
    }

    private void createLog(Study study, String initialMsg) {
        Path studyLogPath = Paths.get(getStudyLogPath(study));
        try {
            Path studyLogDirPath = Paths.get(Common.getStudyLogsPath());
            if (!Files.isDirectory(studyLogDirPath)) {
                Files.createDirectories(studyLogDirPath);
            }

            if (!Files.exists(studyLogPath)) {
                String logLine = nextLogLine(initialMsg, null, null);
                Files.write(studyLogPath, logLine.getBytes(StandardCharsets.ISO_8859_1),
                        StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException e) {
            LOGGER.error("Study log couldn't be written: " + studyLogPath, e);
        }
    }

    public void retireLog(Study study) {
        log(study, "This is the last line of the study log of the study with the UUID " +
                study.getUuid());
        Path studyLogPath = Paths.get(getStudyLogPath(study));
        String retiredFilename = study.getUuid() + "_" + Instant.now().toEpochMilli() + ".retired";
        Path retiredStudyLogPath =
                Paths.get(Common.getStudyLogsPath() + File.separator + retiredFilename);
        if (Files.exists(studyLogPath)) {
            try {
                Files.move(studyLogPath, retiredStudyLogPath);
            } catch (IOException e) {
                LOGGER.error("Study log couldn't be written: " + studyLogPath, e);
            }
        }
    }

    public void logStudyResultDataExporting(List<StudyResult> studyResultList,
            String exportedResultDataStr) {
        if (studyResultList.isEmpty()) {
            return;
        }
        List<ComponentResult> componentResultList = studyResultList.stream()
                .map(StudyResult::getComponentResultList).flatMap(List::stream)
                .collect(Collectors.toList());
        logComponentResultDataExporting(componentResultList, exportedResultDataStr);
    }

    public void logComponentResultDataExporting(List<ComponentResult> componentResultList,
            String exportedResultDataStr) {
        if (componentResultList.isEmpty()) {
            return;
        }

        StudyResult studyResult = componentResultList.get(0).getStudyResult();
        String[] resultDataHashes = componentResultList.stream()
                .filter(cr -> cr.getData() != null)
                .map(cr -> HashUtils.getHashSha256(cr.getData()))
                .toArray(String[]::new);
        String fileHash = HashUtils.getHashSha256(exportedResultDataStr);
        String msg = "Exported component result data to a file";
        log(studyResult.getStudy(), msg, resultDataHashes, fileHash);
    }

    /**
     * Adds a line to the study log belonging to the given ComponentResult: logs the hash of the
     * component result data and that it is going to be stored
     */
    public void logResultDataStoring(ComponentResult componentResult) {
        String resultDataHash = (componentResult.getData() != null) ?
                HashUtils.getHashSha256(componentResult.getData()) : "none";
        String[] resultDataHashes = {resultDataHash};
        Study study = componentResult.getStudyResult().getStudy();
        String componentUuid = componentResult.getComponent().getUuid();
        Long workerId = componentResult.getWorkerId();
        String msg = MessageFormat.format("Stored component result data " +
                "for component with UUID {0} and worker with ID {1}", componentUuid, workerId);
        log(study, msg, resultDataHashes, null);
    }

    /**
     * Adds a line to the study log belonging to the given ComponentResult: logs the hash of the
     * component result data and that it is going to be removed
     */
    public void logResultDataRemoving(ComponentResult componentResult) {
        String resultDataHash = (componentResult.getData() != null) ?
                HashUtils.getHashSha256(componentResult.getData()) : "none";
        String[] resultDataHashes = {resultDataHash};
        Study study = componentResult.getStudyResult().getStudy();
        String componentUuid = componentResult.getComponent().getUuid();
        Long workerId = componentResult.getWorkerId();
        String msg = MessageFormat.format("Removed component result data " +
                "for component with UUID {0} and worker with ID {1}", componentUuid, workerId);
        log(study, msg, resultDataHashes, null);
    }

    /**
     * Adds a line to the study log belonging to the given StudyResult: logs the hashes of all
     * component result data of this StudyResult and that they are going to be removed
     */
    public void logResultDataRemoving(StudyResult studyResult) {
        if (studyResult.getComponentResultList().isEmpty()) {
            return;
        }
        String[] resultDataHashes = studyResult.getComponentResultList().stream()
                .map(cr -> (cr.getData() != null)
                        ? HashUtils.getHashSha256(cr.getData())
                        : "none")
                .toArray(String[]::new);
        String uuids = studyResult.getComponentResultList().stream()
                .map(cr -> cr.getComponent().getUuid())
                .collect(Collectors.joining(", "));
        Study study = studyResult.getStudy();
        Long workerId = studyResult.getWorkerId();
        String msg = MessageFormat.format("Removed component result data " +
                "for components with UUID(s) {0} and worker with ID {1}", uuids, workerId);
        log(study, msg, resultDataHashes, null);
    }

    /**
     * Generates a log entry
     */
    private String nextLogLine(String msg, String[] resultDataHashes,
            String fileHash) throws IOException {
        ObjectNode jsonObj = Json.mapper().createObjectNode();
        jsonObj.put("timestamp", Instant.now().toEpochMilli());
        jsonObj.put("msg", msg);
        if (resultDataHashes != null && resultDataHashes.length > 0) {
            ArrayNode dataHashes = jsonObj.putArray("dataHashes");
            Arrays.stream(resultDataHashes).forEach(dataHashes::add);
        }
        if (fileHash != null) {
            jsonObj.put("fileHash", fileHash);
        }
        return Json.mapper().writer().writeValueAsString(jsonObj);
    }

    private String getMAC() {
        String macStr = "unknown";
        try {
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            while (networks.hasMoreElements()) {
                NetworkInterface network = networks.nextElement();
                byte[] mac = network.getHardwareAddress();

                if (mac != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                    }
                    macStr = sb.toString();
                }
            }
        } catch (SocketException e) {
            LOGGER.info("Couldn't get network MAC address");
        }
        return macStr;
    }

    public Source<ByteString, ?> read(Study study, int lineLimit) {
        // Prepare a chunked text stream (I have no idea what I'm doing here -
        // https://www.playframework.com/documentation/2.5.x/JavaStream)
        return Source
                .<ByteString>actorRef(256, OverflowStrategy.dropNew())
                .mapMaterializedValue(
                        sourceActor -> fillSource(sourceActor, getStudyLogPath(study), lineLimit));
    }

    private Object fillSource(ActorRef sourceActor, String filePath, int lineLimit) {
        File logFile = new File(filePath);
        sourceActor.tell(ByteString.fromString("["), null);
        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(logFile)) {
            String oneLine = reader.readLine();
            int lineNumber = 1;
            while (oneLine != null && (lineLimit == -1 || lineNumber <= lineLimit)) {
                String msg = oneLine;
                oneLine = reader.readLine();
                if (oneLine != null) {
                    msg += ",";
                }
                sourceActor.tell(ByteString.fromString(msg), null);
                lineNumber++;
            }
        } catch (IOException e) {
            sourceActor.tell(ByteString.fromString("\"" + MessagesStrings.COULDNT_OPEN_LOG + "\""),
                    null);
            LOGGER.error("Couldn't open study log " + filePath);
        } finally {
            sourceActor.tell(ByteString.fromString("]"), null);
            sourceActor.tell(new Status.Success(NotUsed.getInstance()), null);
        }
        return null;
    }


}
