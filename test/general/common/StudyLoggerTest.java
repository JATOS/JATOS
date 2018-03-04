package general.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Guice;
import com.google.inject.Injector;
import general.TestHelper;
import models.common.Batch;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
        studyLogger.log(study, "bla bla bla");

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

        studyLogger.retire(study);

        // Check that the log is renamed
        Path retiredLogPath = Paths.get(studyLogger.getRetiredPath(study));
        assertThat(Files.notExists(logPath)).isTrue();
        assertThat(Files.exists(retiredLogPath)).isTrue();

        List<String> content = Files.readAllLines(retiredLogPath);
        JsonNode json = Json.parse(content.get(3)); // First line is empty, second line is init msg
        assertThat(json.has("msg")).isTrue();
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.has("studyUuid")).isTrue();
    }

    @Test
    public void checkLog() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Path logPath = Paths.get(studyLogger.getPath(study));

        // Use all log() methods
        studyLogger.log(study, "bla bla bla");
        studyLogger.log(study, "foo foo foo", study.getDefaultBatch());
        studyLogger.log(study, "bar bar bar", testHelper.getAdmin().getWorker());
        studyLogger.log(study, "fuu fuu fuu", study.getDefaultBatch(),
                testHelper.getAdmin().getWorker());
        studyLogger.log(study, "bir bir bir", Pair.of("birkey", "birvalue"));
        studyLogger.log(study, Json.newObject().put("burkey", "burvalue"));

        // Check they wrote something into the log
        List<String> content = Files.readAllLines(logPath);
        // First line is always empty and second line is the initial msg
        assertThat(content.get(3)).contains("bla bla bla");
        assertThat(content.get(4)).contains("foo foo foo");
        assertThat(content.get(4)).contains("\"batchId\":" + study.getDefaultBatch().getId());
        assertThat(content.get(5)).contains("bar bar bar");
        assertThat(content.get(5))
                .contains("\"workerId\":" + testHelper.getAdmin().getWorker().getId());
        assertThat(content.get(6)).contains("fuu fuu fuu");
        assertThat(content.get(6)).contains("\"batchId\":" + study.getDefaultBatch().getId());
        assertThat(content.get(6))
                .contains("\"workerId\":" + testHelper.getAdmin().getWorker().getId());
        assertThat(content.get(7)).contains("bir bir bir");
        assertThat(content.get(7)).contains("\"birkey\":\"birvalue\"");
        assertThat(content.get(8)).contains("\"burkey\":\"burvalue\"");
    }

    @Test
    public void checkLogStudyResultDataExporting() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Worker worker = testHelper.getAdmin().getWorker();
        Batch batch = study.getDefaultBatch();

        List<StudyResult> studyResultList = get2StudyResults(study, worker, batch);

        String exportedResultDataStr = "whole exported result data";
        studyLogger.logStudyResultDataExporting(studyResultList, exportedResultDataStr);

        Path logPath = Paths.get(studyLogger.getPath(study));
        List<String> content = Files.readAllLines(logPath);
        JsonNode json = Json.parse(content.get(3)); // First line is empty, second line is init msg

        assertThat(json.has("msg")).isTrue();
        assertThat(json.has("timestamp")).isTrue();

        // Check file hash
        assertThat(json.has("fileHash")).isTrue();
        assertThat(json.get("fileHash").asText()).isEqualTo(
                HashUtils.getHash(exportedResultDataStr, StudyLogger.SHA_256));

        check4ResultDataHashes(json);

        // Check worker IDs
        assertThat(json.has("workerIds")).isTrue();
        json.get("workerIds").forEach(node -> assertThat(node.asLong()).isEqualTo(worker.getId()));
    }

    @Test
    public void checkLogComponentResultDataExporting() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Worker worker = testHelper.getAdmin().getWorker();
        Batch batch = study.getDefaultBatch();

        List<ComponentResult> componentResultList = get4ComponentResults(study, worker, batch);

        String exportedResultDataStr = "whole exported result data";
        studyLogger.logComponentResultDataExporting(componentResultList, exportedResultDataStr);

        Path logPath = Paths.get(studyLogger.getPath(study));
        List<String> content = Files.readAllLines(logPath);
        JsonNode json = Json.parse(content.get(3)); // First line is empty, second line is init msg

        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.has("msg")).isTrue();

        // Check file hash
        assertThat(json.has("fileHash")).isTrue();
        assertThat(json.get("fileHash").asText()).isEqualTo(
                HashUtils.getHash(exportedResultDataStr, StudyLogger.SHA_256));
        check4ResultDataHashes(json);

        // Check worker IDs
        assertThat(json.has("workerIds")).isTrue();
        json.get("workerIds").forEach(node -> assertThat(node.asLong()).isEqualTo(worker.getId()));
    }

    @Test
    public void checkLogResultDataStoring() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Worker worker = testHelper.getAdmin().getWorker();
        Batch batch = study.getDefaultBatch();

        StudyResult studyResult1 = new StudyResult(study, batch, worker);
        ComponentResult componentResult = new ComponentResult(study.getFirstComponent());
        componentResult.setStudyResult(studyResult1);
        componentResult.setData("result data 1");
        studyResult1.addComponentResult(componentResult);

        studyLogger.logResultDataStoring(componentResult);

        Path logPath = Paths.get(studyLogger.getPath(study));
        List<String> content = Files.readAllLines(logPath);
        JsonNode json = Json.parse(content.get(3)); // First line is empty, second line is init msg

        assertThat(json.has("msg")).isTrue();
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.has("componentUuid")).isTrue();
        assertThat(json.get("componentUuid").asText())
                .isEqualTo(study.getFirstComponent().getUuid());
        assertThat(json.has("workerId")).isTrue();
        assertThat(json.get("workerId").asLong()).isEqualTo(worker.getId());
        assertThat(json.has("dataHash")).isTrue();
        assertThat(json.get("dataHash").asText()).isEqualTo(
                HashUtils.getHash("result data 1", StudyLogger.SHA_256));
    }

    @Test
    public void checkLogResultDataRemoving() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Worker worker = testHelper.getAdmin().getWorker();
        Batch batch = study.getDefaultBatch();

        List<ComponentResult> componentResultList = get4ComponentResults(study, worker, batch);

        studyLogger.logResultDataRemoving(componentResultList);

        Path logPath = Paths.get(studyLogger.getPath(study));
        List<String> content = Files.readAllLines(logPath);
        JsonNode json = Json.parse(content.get(3)); // First line is empty, second line is init msg

        assertThat(json.has("msg")).isTrue();
        assertThat(json.has("timestamp")).isTrue();

        check4ResultDataHashes(json);

        // Check component UUID
        assertThat(json.has("componentUuids")).isTrue();
        json.get("componentUuids").forEach(
                node -> assertThat(node.asText()).isEqualTo(study.getFirstComponent().getUuid()));

        // Check worker IDs
        assertThat(json.has("workerIds")).isTrue();
        json.get("workerIds").forEach(node -> assertThat(node.asLong()).isEqualTo(worker.getId()));
    }

    @Test
    public void checkLogStudyResultDataRemoving() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Worker worker = testHelper.getAdmin().getWorker();
        Batch batch = study.getDefaultBatch();

        List<StudyResult> studyResultList = get2StudyResults(study, worker, batch);

        studyLogger.logStudyResultDataRemoving(studyResultList);

        Path logPath = Paths.get(studyLogger.getPath(study));
        List<String> content = Files.readAllLines(logPath);
        JsonNode json = Json.parse(content.get(3)); // First line is empty, second line is init msg

        assertThat(json.has("msg")).isTrue();
        assertThat(json.has("timestamp")).isTrue();

        check4ResultDataHashes(json);

        // Check component UUID
        assertThat(json.has("componentUuids")).isTrue();
        json.get("componentUuids").forEach(
                node -> assertThat(node.asText()).isEqualTo(study.getFirstComponent().getUuid()));

        // Check worker IDs
        assertThat(json.has("workerIds")).isTrue();
        json.get("workerIds").forEach(node -> assertThat(node.asLong()).isEqualTo(worker.getId()));
    }

    private void checkInitEntry(Study study) throws IOException {
        Path logPath = Paths.get(studyLogger.getPath(study));
        List<String> content = Files.readAllLines(logPath);
        JsonNode json = Json.parse(content.get(1)); // First line is empty, second line is init msg
        assertThat(json.has("msg")).isTrue();
        assertThat(json.has("timestamp")).isTrue();
        assertThat(json.has("studyUuid")).isTrue();
        assertThat(json.get("studyUuid").asText()).isEqualTo(study.getUuid());
        assertThat(json.has("jatosVersion")).isTrue();
        assertThat(json.has("serversMac")).isTrue();
        assertThat(json.get("serversMac").asText()).isEqualTo(Common.getMac());
        assertThat(json.has("hashFunction")).isTrue();
        assertThat(json.get("hashFunction").asText()).isEqualTo(StudyLogger.SHA_256);
    }

    private List<StudyResult> get2StudyResults(Study study, Worker worker, Batch batch) {
        StudyResult studyResult1 = new StudyResult(study, batch, worker);
        ComponentResult componentResult1 = new ComponentResult(study.getFirstComponent());
        componentResult1.setStudyResult(studyResult1);
        componentResult1.setData("result data 1");
        studyResult1.addComponentResult(componentResult1);
        ComponentResult componentResult2 = new ComponentResult(study.getFirstComponent());
        componentResult2.setStudyResult(studyResult1);
        componentResult2.setData("result data 2");
        studyResult1.addComponentResult(componentResult2);

        StudyResult studyResult2 = new StudyResult(study, batch, worker);
        ComponentResult componentResult3 = new ComponentResult(study.getFirstComponent());
        componentResult3.setStudyResult(studyResult2);
        componentResult3.setData("result data 3");
        studyResult2.addComponentResult(componentResult3);
        ComponentResult componentResult4 = new ComponentResult(study.getFirstComponent());
        componentResult4.setStudyResult(studyResult2);
        // component result 4 gets no result data
        studyResult2.addComponentResult(componentResult4);

        List<StudyResult> studyResultList = new ArrayList<>();
        studyResultList.add(studyResult1);
        studyResultList.add(studyResult2);
        return studyResultList;
    }

    private List<ComponentResult> get4ComponentResults(Study study, Worker worker, Batch batch) {
        StudyResult studyResult = new StudyResult(study, batch, worker);
        ComponentResult componentResult1 = new ComponentResult(study.getFirstComponent());
        componentResult1.setData("result data 1");
        componentResult1.setStudyResult(studyResult);
        ComponentResult componentResult2 = new ComponentResult(study.getFirstComponent());
        componentResult2.setData("result data 2");
        componentResult2.setStudyResult(studyResult);
        ComponentResult componentResult3 = new ComponentResult(study.getFirstComponent());
        componentResult3.setData("result data 3");
        componentResult3.setStudyResult(studyResult);
        ComponentResult componentResult4 = new ComponentResult(study.getFirstComponent());
        // component result does not get a result data
        componentResult4.setStudyResult(studyResult);

        List<ComponentResult> componentResultList = new ArrayList<>();
        componentResultList.add(componentResult1);
        componentResultList.add(componentResult2);
        componentResultList.add(componentResult3);
        componentResultList.add(componentResult4);
        return componentResultList;
    }

    private void check4ResultDataHashes(JsonNode json) {
        String hash1 = HashUtils.getHash("result data 1", StudyLogger.SHA_256);
        String hash2 = HashUtils.getHash("result data 2", StudyLogger.SHA_256);
        String hash3 = HashUtils.getHash("result data 3", StudyLogger.SHA_256);
        assertThat(json.has("dataHashes"));
        json.get("dataHashes").forEach(node -> assertThat(node.asText())
                .isIn(hash1, hash2, hash3, "no data"));
    }

}
