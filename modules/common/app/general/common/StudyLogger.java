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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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
 * remove results in bulk is not efficient (ResultRemover)
 * tests?
 * detect log file changes?
 * retire filename wrong
 * What if log file deleted: right now GUI nothing happens
 * GUI: study log validator
 * GUI: show last 1000 lines, reversed, as raw and pretty JSON
 * comments in studylogger, studies and beautify
 */
@Singleton
public class StudyLogger {

    private static final Logger.ALogger LOGGER = Logger.of(StudyLogger.class);

    private static final String LOGS_PATH =
            Common.getBasepath() + File.separator + "logs" + File.separator;
    public static final int HASH_SIZE = 64;

    public String getFilename(Study study) {
        return study.getUuid() + ".log";
    }

    public String getPath(Study study) {
        return LOGS_PATH + getFilename(study);
    }

    public void log(StudyResult studyResult, String msg) {
        log(studyResult.getStudy(), msg, null, null);
    }

    public void log(Study study, String msg) {
        log(study, msg, null, null);
    }

    public void log(Study study, String msg, String[] resultDataHashes, String fileHash) {
        Path studyLog = Paths.get(getPath(study));

        try {
            if (!Files.exists(studyLog)) {
                createLog(study);
            }

            String logLine = "\n" + nextLogLine(studyLog, msg, resultDataHashes, fileHash);
            Files.write(studyLog, logLine.getBytes(StandardCharsets.ISO_8859_1),
                    StandardOpenOption.APPEND);

        } catch (IOException e) {
            LOGGER.error("Study log " + studyLog.getFileName() + " couldn't be written");
        }
    }

    public void createLog(Study study) {
        Path studyLog = Paths.get(getPath(study));

        try {
            if (!Files.exists(studyLog)) {
                String initialMsg = "Started study log, " +
                        "JATOS version " + Common.getJatosVersion() + ", " +
                        "MAC address " + getMAC();
                String logLine = nextLogLine(studyLog, initialMsg, null, null);
                Files.write(studyLog, logLine.getBytes(StandardCharsets.ISO_8859_1),
                        StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException e) {
            LOGGER.error("Study log " + studyLog.getFileName() + " couldn't be written");
        }
    }

    public void retireLog(Study study) {
        log(study, "This is the last line of the study log of the study with the UUID " +
                study.getUuid());
        Path studyLog = Paths.get(getPath(study));
        Path retiredStudyLog = Paths.get(
                LOGS_PATH + study.getUuid() + "_" + Instant.now().toEpochMilli() + ".log");
        if (Files.exists(studyLog)) {
            try {
                Files.move(studyLog, retiredStudyLog);
            } catch (IOException e) {
                LOGGER.error("Study log " + studyLog.getFileName() + " couldn't be written");
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

    public void logResultDataRemoving(ComponentResult componentResult) {
        String resultDataHash = (componentResult.getData() != null) ?
                HashUtils.getHashSha256(componentResult.getData()) : "no data";
        String[] resultDataHashes = {resultDataHash};
        Study study = componentResult.getStudyResult().getStudy();
        String componentUuid = componentResult.getComponent().getUuid();
        Long workerId = componentResult.getWorkerId();
        String msg = MessageFormat.format("Removed component result data " +
                "for component with UUID {0} and worker with ID {1}", componentUuid, workerId);
        log(study, msg, resultDataHashes, null);
    }

    public void logResultDataStoring(ComponentResult componentResult) {
        String resultDataHash = (componentResult.getData() != null) ?
                HashUtils.getHashSha256(componentResult.getData()) : "no data";
        String[] resultDataHashes = {resultDataHash};
        Study study = componentResult.getStudyResult().getStudy();
        String componentUuid = componentResult.getComponent().getUuid();
        Long workerId = componentResult.getWorkerId();
        String msg = MessageFormat.format("Stored component result data " +
                "for component with UUID {0} and worker with ID {1}", componentUuid, workerId);
        log(study, msg, resultDataHashes, null);
    }

    public void logResultDataRemoving(StudyResult studyResult) {
        String[] resultDataHashes = studyResult.getComponentResultList().stream()
                .map(cr -> (cr.getData() != null)
                        ? HashUtils.getHashSha256(cr.getData())
                        : "no data")
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
    private String nextLogLine(Path studyLog, String msg, String[] resultDataHashes,
            String fileHash) throws IOException {
        ObjectNode jsonObj = Json.mapper().createObjectNode();
        jsonObj.put("timestamp", Instant.now().toEpochMilli());
        jsonObj.put("msg", msg);
        String lastHash = Files.exists(studyLog) ? getLastHash(studyLog) : "";
        jsonObj.put("hash", lastHash);
        if (resultDataHashes != null && resultDataHashes.length > 0) {
            ArrayNode dataHashes = jsonObj.putArray("dataHashes");
            Arrays.stream(resultDataHashes).forEach(dataHashes::add);
        }
        if (fileHash != null) {
            jsonObj.put("fileHash", fileHash);
        }
        String newHash = HashUtils.getHashSha256(
                Json.mapper().writer().writeValueAsString(jsonObj));
        jsonObj.put("hash", newHash);
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

    private String getLastHash(Path studyLog) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(HASH_SIZE + 2);
        FileChannel channel = FileChannel.open(studyLog, StandardOpenOption.READ);
        channel.read(buffer, channel.size() - HASH_SIZE + 2);
        String str = new String(buffer.array());
        return str.substring(0, str.length() - 2);
    }

    public Source<ByteString, ?> read(Study study, int lineLimit) {
        // Prepare a chunked text stream (I have no idea what I'm doing here -
        // https://www.playframework.com/documentation/2.5.x/JavaStream)
        return Source
                .<ByteString>actorRef(256, OverflowStrategy.dropNew())
                .mapMaterializedValue(
                        sourceActor -> fillSource(sourceActor, getPath(study), lineLimit));
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
            sourceActor.tell(ByteString.fromString("]"), null);
            sourceActor.tell(new Status.Failure(e), null);
        }
        sourceActor.tell(ByteString.fromString("]"), null);
        sourceActor.tell(new Status.Success(NotUsed.getInstance()), null);
        return null;
    }

}
