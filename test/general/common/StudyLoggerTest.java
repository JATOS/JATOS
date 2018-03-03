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
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.libs.Json;
import services.gui.ResultService;
import services.gui.ResultTestHelper;
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
    private JPAApi jpaApi;

    @Inject
    private TestHelper testHelper;

    @Inject
    private ResultTestHelper resultTestHelper;

    @Inject
    private StudyLogger studyLogger;

    @Inject
    private ResultService resultService;

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
    public void checkCreateAndRecreate() throws Exception {
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
    }

    @Test
    public void checkRetire() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Check that log is created
        Path logPath = Paths.get(studyLogger.getPath(study));
        assertThat(Files.isReadable(logPath)).isTrue();

        studyLogger.retire(study);

        // Check that the log is renamed
        Path retiredLogPath = Paths.get(studyLogger.getRetiredPath(study));
        assertThat(Files.notExists(logPath)).isTrue();
        assertThat(Files.exists(retiredLogPath)).isTrue();
    }

    @Test
    public void checkLog() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Path logPath = Paths.get(studyLogger.getPath(study));

        studyLogger.log(study, "bla bla bla");
        studyLogger.log(study, "foo foo foo", study.getDefaultBatch());
        studyLogger.log(study, "bar bar bar", testHelper.getAdmin().getWorker());
        studyLogger.log(study, "fuu fuu fuu", study.getDefaultBatch(),
                testHelper.getAdmin().getWorker());
        studyLogger.log(study, "bir bir bir", Pair.of("birkey", "birvalue"));
        studyLogger.log(study, Json.newObject().put("burkey", "burvalue"));

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
        componentResult4.setData("result data 4");
        studyResult2.addComponentResult(componentResult4);

        List<StudyResult> studyResultList = new ArrayList<>();
        studyResultList.add(studyResult1);
        studyResultList.add(studyResult2);

        String exportedResultDataStr = "whole exported result data";
        studyLogger.logStudyResultDataExporting(studyResultList, exportedResultDataStr);

        Path logPath = Paths.get(studyLogger.getPath(study));
        List<String> content = Files.readAllLines(logPath);
        JsonNode json = Json.parse(content.get(3)); // First line is empty, second line is init msg

        // Check file hash
        assertThat(json.has("fileHash"));
        assertThat(json.get("fileHash").asText()).isEqualTo(
                HashUtils.getHash(exportedResultDataStr, StudyLogger.HASH_FUNCTION));

        // Check all 4 result data hashes
        String hash1 = HashUtils.getHash("result data 1", StudyLogger.HASH_FUNCTION);
        String hash2 = HashUtils.getHash("result data 2", StudyLogger.HASH_FUNCTION);
        String hash3 = HashUtils.getHash("result data 3", StudyLogger.HASH_FUNCTION);
        String hash4 = HashUtils.getHash("result data 4", StudyLogger.HASH_FUNCTION);
        assertThat(json.has("dataHashes"));
        json.get("dataHashes")
                .forEach(node -> assertThat(node.asText()).isIn(hash1, hash2, hash3, hash4));

        // Check worker IDs
        assertThat(json.has("workerIds"));
        json.get("workerIds").forEach(node -> assertThat(node.asLong()).isEqualTo(worker.getId()));
    }

    @Test
    public void checkLogComponentResultDataExporting() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Worker worker = testHelper.getAdmin().getWorker();
        Batch batch = study.getDefaultBatch();

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
        componentResult4.setData("result data 4");
        componentResult4.setStudyResult(studyResult);

        List<ComponentResult> componentResultList = new ArrayList<>();
        componentResultList.add(componentResult1);
        componentResultList.add(componentResult2);
        componentResultList.add(componentResult3);
        componentResultList.add(componentResult4);

        String exportedResultDataStr = "whole exported result data";
        studyLogger.logComponentResultDataExporting(componentResultList, exportedResultDataStr);

        Path logPath = Paths.get(studyLogger.getPath(study));
        List<String> content = Files.readAllLines(logPath);
        JsonNode json = Json.parse(content.get(3)); // First line is empty, second line is init msg

        // Check file hash
        assertThat(json.has("fileHash"));
        assertThat(json.get("fileHash").asText()).isEqualTo(
                HashUtils.getHash(exportedResultDataStr, StudyLogger.HASH_FUNCTION));

        // Check all 4 result data hashes
        String hash1 = HashUtils.getHash("result data 1", StudyLogger.HASH_FUNCTION);
        String hash2 = HashUtils.getHash("result data 2", StudyLogger.HASH_FUNCTION);
        String hash3 = HashUtils.getHash("result data 3", StudyLogger.HASH_FUNCTION);
        String hash4 = HashUtils.getHash("result data 4", StudyLogger.HASH_FUNCTION);
        assertThat(json.has("dataHashes"));
        json.get("dataHashes")
                .forEach(node -> assertThat(node.asText()).isIn(hash1, hash2, hash3, hash4));

        // Check worker IDs
        assertThat(json.has("workerIds"));
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

        assertThat(json.has("msg"));
        assertThat(json.has("componentUuid"));
        assertThat(json.get("componentUuid").asText())
                .isEqualTo(study.getFirstComponent().getUuid());
        assertThat(json.has("workerId"));
        assertThat(json.get("workerId").asLong()).isEqualTo(worker.getId());
        assertThat(json.has("dataHash"));
        assertThat(json.get("dataHash").asText()).isEqualTo(
                HashUtils.getHash("result data 1", StudyLogger.HASH_FUNCTION));
    }

}
