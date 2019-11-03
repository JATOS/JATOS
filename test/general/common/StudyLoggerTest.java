package general.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Guice;
import com.google.inject.Injector;
import general.TestHelper;
import models.common.*;
import models.common.workers.Worker;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.ApplicationLoader;
import play.Environment;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.libs.Json;
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
public class StudyLoggerTest {

    private Injector injector;

    @Inject
    private TestHelper testHelper;

    @Inject
    private StudyLogger studyLogger;

    @Before
    public void startApp() throws Exception {
        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        injector = Guice.createInjector(builder.applicationModule());
        injector.injectMembers(this);
    }

    @After
    public void stopApp() throws Exception {
        // Clean up
        testHelper.removeAllStudies();
        testHelper.removeStudyAssetsRootDir();
        testHelper.removeAllStudyLogs();
    }

    @Test
    public void simpleCheck() {
        int a = 1 + 1;
        assertThat(a).isEqualTo(2);
    }

    @Test
    public void checkCreate() throws Exception {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Check that log is created
        Path studyLogPath = Paths.get(studyLogger.getPath(study));
        assertThat(Files.isReadable(studyLogPath)).isTrue();
        checkInitEntry(study);
    }

    @Test
    public void checkRecreate() throws Exception {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Check that log is created
        Path studyLogPath = Paths.get(studyLogger.getPath(study));
        assertThat(Files.isReadable(studyLogPath)).isTrue();

        // Now delete the log
        Files.delete(studyLogPath);
        assertThat(Files.notExists(studyLogPath)).isTrue();

        // Write something into the log
        studyLogger.log(study, testHelper.getAdmin(), "bla bla bla");

        // Check that the log is recreated
        assertThat(Files.isReadable(studyLogPath)).isTrue();

        checkInitEntry(study);
    }

    @Test
    public void checkRetire() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

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
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Path logPath = Paths.get(studyLogger.getPath(study));
        User user = testHelper.getAdmin();

        // Use all log() methods
        studyLogger.log(study, user, "bla bla bla");
        studyLogger.log(study, user, "foo foo foo", study.getDefaultBatch());
        studyLogger.log(study, "bar bar bar", testHelper.getAdmin().getWorker());
        studyLogger.log(study, "fuu fuu fuu", study.getDefaultBatch(), testHelper.getAdmin().getWorker());
        studyLogger.log(study, user, "bir bir bir", Pair.of("birkey", "birvalue"));

        // Check they wrote something into the log
        List<String> content = Files.readAllLines(logPath);
        // First line is always empty, second line is the initial msg, third line is study created, fourth line is
        // study description
        assertThat(content.get(4)).contains("bla bla bla");
        assertThat(content.get(5)).contains("foo foo foo");
        assertThat(content.get(5)).contains("\"batchId\":" + study.getDefaultBatch().getId());
        assertThat(content.get(6)).contains("bar bar bar");
        assertThat(content.get(6)).contains("\"workerId\":" + testHelper.getAdmin().getWorker().getId());
        assertThat(content.get(7)).contains("fuu fuu fuu");
        assertThat(content.get(7)).contains("\"batchId\":" + study.getDefaultBatch().getId());
        assertThat(content.get(7)).contains("\"workerId\":" + testHelper.getAdmin().getWorker().getId());
        assertThat(content.get(8)).contains("bir bir bir");
        assertThat(content.get(8)).contains("\"birkey\":\"birvalue\"");
    }

    @Test
    public void checkLogResultDataStoring() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Worker worker = testHelper.getAdmin().getWorker();
        Batch batch = study.getDefaultBatch();

        StudyResult studyResult1 = new StudyResult(study, batch, worker);
        ComponentResult componentResult = new ComponentResult(study.getFirstComponent().get());
        componentResult.setStudyResult(studyResult1);
        componentResult.setData("result data 1");
        studyResult1.addComponentResult(componentResult);

        studyLogger.logResultDataStoring(componentResult);

        Path logPath = Paths.get(studyLogger.getPath(study));
        List<String> content = Files.readAllLines(logPath);
        JsonNode json = Json.parse(content.get(content.size() - 1)); // get last line from log

        assertThat(json.has("msg")).isTrue();
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.has("componentUuid")).isTrue();
        assertThat(json.get("componentUuid").asText()).isEqualTo(study.getFirstComponent().get().getUuid());
        assertThat(json.has("workerId")).isTrue();
        assertThat(json.get("workerId").asLong()).isEqualTo(worker.getId());
        assertThat(json.has("dataHash")).isTrue();
        assertThat(json.get("dataHash").asText()).isEqualTo(HashUtils.getHash("result data 1", HashUtils.SHA_256));
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
