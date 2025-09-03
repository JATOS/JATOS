package modules.common.general;

import com.fasterxml.jackson.databind.JsonNode;
import general.common.Common;
import general.common.StudyLogger;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyLink;
import models.common.StudyResult;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import play.libs.Json;
import testutils.JatosTest;
import utils.common.HashUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests StudyLogger
 *
 * @author Kristian Lange
 */
public class StudyLoggerTest extends JatosTest {

    @Inject
    private StudyLogger studyLogger;

    @Test
    public void checkCreate() throws Exception {
        Study study = getExampleStudy();

        // Check that log is created
        Path studyLogPath = Paths.get(studyLogger.getPath(study));
        assertThat(Files.isReadable(studyLogPath)).isTrue();
        checkInitEntry(study);
    }

    @Test
    public void checkRecreate() throws Exception {
        Study study = getExampleStudy();

        // Check that log is created
        Path studyLogPath = Paths.get(studyLogger.getPath(study));
        assertThat(Files.isReadable(studyLogPath)).isTrue();

        // Now delete the log
        Files.delete(studyLogPath);
        assertThat(Files.notExists(studyLogPath)).isTrue();

        // Write something into the log
        studyLogger.log(study, admin, "bla bla bla");

        // Check that the log is recreated
        assertThat(Files.isReadable(studyLogPath)).isTrue();

        checkInitEntry(study);
    }

    @Test
    public void checkRetire() throws IOException {
        Study study = getExampleStudy();

        // Check that log is created
        Path logPath = Paths.get(studyLogger.getPath(study));
        assertThat(Files.isReadable(logPath)).isTrue();

        String retiredLogFilename = studyLogger.retire(study);

        // Check that the log is renamed
        Path retiredLogPath = Paths.get(Common.getStudyLogsPath() + File.separator + retiredLogFilename);
        assertThat(Files.notExists(logPath)).isTrue();
        assertThat(Files.exists(retiredLogPath)).isTrue();

        List<String> content = Files.readAllLines(retiredLogPath);
        JsonNode json = Json.parse(content.get(content.size() - 1)); // get last line from log
        assertThat(json.has("msg")).isTrue();
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.has("studyUuid")).isTrue();
    }

    @Test
    public void checkLog() throws IOException {
        Study study = getExampleStudy();
        Path logPath = Paths.get(studyLogger.getPath(study));
        StudyLink studyLink = new StudyLink(study.getDefaultBatch(), admin.getWorker());

        // Use all log() methods
        studyLogger.log(study, admin, "log with user");
        studyLogger.log(study, "log with worker", admin.getWorker());
        studyLogger.log(studyLink, "log with study link and worker", admin.getWorker());
        studyLogger.log(study, admin, "log with batch", study.getDefaultBatch());
        studyLogger.log(study, admin, "log with Pair", Pair.of("mykey", "myvalue"));
        studyLogger.log(study, admin, "log non-ASCII 你经常来吗"); // handles ISO_8859_1 only

        // Check they wrote something into the log
        List<String> content = Files.readAllLines(logPath);
        // First line is always empty, second line is the initial msg, third line is study created, fourth line is
        // study description
        assertThat(Json.parse(content.get(4)).get("msg").textValue()).isEqualTo("log with user");
        assertThat(Json.parse(content.get(5)).get("msg").textValue()).isEqualTo("log with worker");
        assertThat(Json.parse(content.get(6)).get("msg").textValue()).isEqualTo("log with study link and worker");
        assertThat(Json.parse(content.get(6)).get("workerId").asLong()).isEqualTo(admin.getWorker().getId());
        assertThat(Json.parse(content.get(7)).get("msg").textValue()).isEqualTo("log with batch");
        assertThat(Json.parse(content.get(7)).get("batchId").asLong()).isEqualTo(study.getDefaultBatch().getId());
        assertThat(Json.parse(content.get(8)).get("msg").textValue()).isEqualTo("log with Pair");
        assertThat(Json.parse(content.get(8)).get("mykey").textValue()).isEqualTo("myvalue");
        assertThat(Json.parse(content.get(9)).get("msg").textValue()).isEqualTo("log non-ASCII ?????");
    }

    @Test
    public void checkLogResultDataStoring() throws IOException {
        Study study = getExampleStudy();
        StudyLink studyLink = new StudyLink(study.getDefaultBatch(), admin.getWorker());
        StudyResult studyResult = new StudyResult(studyLink, admin.getWorker());
        ComponentResult componentResult = new ComponentResult(study.getFirstComponent().get());
        componentResult.setStudyResult(studyResult);
        studyResult.addComponentResult(componentResult);
        String data = "result data 1";

        studyLogger.logResultDataStoring(componentResult, data, false);

        Path logPath = Paths.get(studyLogger.getPath(study));
        List<String> content = Files.readAllLines(logPath);
        JsonNode json = Json.parse(content.get(content.size() - 1)); // get last line from log

        assertThat(json.has("msg")).isTrue();
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.has("componentUuid")).isTrue();
        assertThat(json.get("componentUuid").asText()).isEqualTo(study.getFirstComponent().get().getUuid());
        assertThat(json.has("workerId")).isTrue();
        assertThat(json.get("workerId").asLong()).isEqualTo(admin.getWorker().getId());
        assertThat(json.has("dataHash")).isTrue();
        assertThat(json.get("dataHash").asText()).isEqualTo(HashUtils.getHash("result data 1", HashUtils.SHA_256));
    }

    @Test
    public void checkLogResultFileUploading() throws IOException {
        Study study = getExampleStudy();
        StudyLink studyLink = new StudyLink(study.getDefaultBatch(), admin.getWorker());
        StudyResult studyResult = new StudyResult(studyLink, admin.getWorker());
        ComponentResult componentResult = new ComponentResult(study.getFirstComponent().get());
        componentResult.setStudyResult(studyResult);
        studyResult.addComponentResult(componentResult);
        Path uploadedFile = Paths.get("test/resources/example.png");

        studyLogger.logResultUploading(uploadedFile, componentResult);

        Path logPath = Paths.get(studyLogger.getPath(study));
        List<String> content = Files.readAllLines(logPath);
        JsonNode json = Json.parse(content.get(content.size() - 1)); // get last line from log

        assertThat(json.has("msg")).isTrue();
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.has("componentUuid")).isTrue();
        assertThat(json.get("componentUuid").asText()).isEqualTo(study.getFirstComponent().get().getUuid());
        assertThat(json.has("workerId")).isTrue();
        assertThat(json.get("workerId").asLong()).isEqualTo(admin.getWorker().getId());
        assertThat(json.has("fileName")).isTrue();
        assertThat(json.get("fileName").asText()).isEqualTo(uploadedFile.getFileName().toString());
        assertThat(json.has("fileHash")).isTrue();
        assertThat(json.get("fileHash").asText()).isEqualTo(HashUtils.getHash(uploadedFile, HashUtils.SHA_256));
    }

    private void checkInitEntry(Study study) throws IOException {
        Path logPath = Paths.get(studyLogger.getPath(study));
        List<String> content = Files.readAllLines(logPath);
        JsonNode json = Json.parse(content.get(1)); // First line is empty, second line is init msg
        assertThat(json.has("msg")).isTrue();
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.has("studyUuid")).isTrue();
        assertThat(json.get("studyUuid").asText()).isEqualTo(study.getUuid());
        assertThat(json.has("serversMac")).isTrue();
        assertThat(json.get("serversMac").asText()).isEqualTo(Common.getMac());
        assertThat(json.has("hashFunction")).isTrue();
        assertThat(json.get("hashFunction").asText()).isEqualTo(HashUtils.SHA_256);
    }

}
