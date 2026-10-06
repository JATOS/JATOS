package gui;

import daos.common.StudyDao;
import general.common.StudyLogger;
import http.common.Http.Context;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Synchronization;
import models.common.*;
import models.common.workers.PersonalMultipleWorker;
import org.hibernate.Session;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import play.Application;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.Helpers;
import services.gui.BatchService;
import services.gui.StudyService;
import utils.common.IOUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Exercises real Hibernate deletes against the evolved schema in single-node mode. Each fixture has active and
 * historical group members, component results, links, uploads, a sibling batch, and an unrelated study sharing a
 * worker. Nested service calls must flush child deletion before parent cascades but must retain files until the outer
 * transaction commits. The optional MySQL test creates and drops only its own uniquely named database.
 */
public class DeletionCascadeIntegrationTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void h2DeletionAndRollback() throws Exception {
        check("org.h2.Driver", "jdbc:h2:mem:deletion-" + UUID.randomUUID() +
                ";MODE=MYSQL;DATABASE_TO_UPPER=FALSE;IGNORECASE=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test
    public void mysqlDeletionAndRollback() throws Exception {
        String server = System.getenv("JATOS_TEST_MYSQL_SERVER");
        assumeTrue("Set JATOS_TEST_MYSQL_SERVER to a disposable server URL ending in /", server != null && !server.isEmpty());
        assertTrue("Server URL must end in / without a database name", server.endsWith("/"));
        String user = System.getenv().getOrDefault("JATOS_TEST_MYSQL_USER", "root");
        String password = System.getenv().getOrDefault("JATOS_TEST_MYSQL_PASSWORD", "");
        String options = System.getenv().getOrDefault("JATOS_TEST_MYSQL_OPTIONS", "");
        String name = "jatos_deletion_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(server, user, password); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + name + "`");
            try {
                check("com.mysql.cj.jdbc.Driver", server + name + (options.isEmpty() ? "" : "?" + options), user, password);
            } finally {statement.execute("DROP DATABASE `" + name + "`");}
        }
    }

    private enum Outcome {COMMIT, EXCEPTION, ROLLBACK_ONLY, COMMIT_FAILURE}

    private record Run(Long result, Long component, Long group, boolean historical) {}

    private record Fixture(Long study, Long batch, Long sibling, Long otherStudy, Long worker,
                           String dir, String uuid, List<Run> selected, List<Run> siblingRuns, List<Run> otherRuns) {}

    private void check(String driver, String url, String username, String password) throws Exception {
        String root = temp.getRoot().getAbsolutePath();
        Application app = new GuiceApplicationBuilder()
                .configure("db.default.driver", driver).configure("db.default.url", url)
                .configure("db.default.username", username).configure("db.default.password", password)
                .configure("jatos.multiNode", false)
                .configure("jatos.studyAssetsRootPath", root + "/assets")
                .configure("jatos.studyLogs.path", root + "/study-logs")
                .configure("jatos.resultUploads.path", root + "/uploads")
                .configure("jatos.logs.path", root + "/logs").configure("jatos.tmpPath", root + "/tmp")
                .build();
        try {
            Helpers.start(app);
            JPAApi jpa = app.injector().instanceOf(JPAApi.class);
            StudyService studies = app.injector().instanceOf(StudyService.class);
            BatchService batches = app.injector().instanceOf(BatchService.class);
            Context.setCurrent(new Context(Helpers.fakeRequest().build()));
            Context.current().args().put(SIGNEDIN_USER, jpa.withTransaction(em -> {
                return em.find(User.class, "admin");
            }));
            if (driver.equals("com.mysql.cj.jdbc.Driver")) {
                // Demonstrate the original defect on single-node MySQL, with the real evolved FK constraints.
                Fixture original = fixture(jpa, app);
                RuntimeException failure = assertThrows(RuntimeException.class, () -> jpa.withTransaction((EntityManager em) -> {
                    em.createNativeQuery("DELETE FROM Batch WHERE id = :id").setParameter("id", original.batch).executeUpdate();
                }));
                assertTrue(failure.toString(), hasMessage(failure, "foreign key constraint fails"));
                assertRows(jpa, original.selected, true);
                assertFiles(original.selected, true);
            }
            for (boolean wholeStudy : new boolean[]{false, true}) {
                for (Outcome outcome : Outcome.values()) {
                    Fixture f = fixture(jpa, app);
                    String logBefore = Files.readString(Path.of(root, "study-logs", f.uuid + ".log"));
                    Runnable deletion = () -> jpa.withTransaction((EntityManager em) -> {
                        if (wholeStudy) studies.removeStudyInclAssets(em.find(Study.class, f.study));
                        else batches.remove(em.find(Batch.class, f.batch));
                        // This service call joined our transaction. Neither flushing nor returning may remove files.
                        assertFiles(f.selected, true);
                        assertTrue(Files.exists(Path.of(root, "assets", f.dir, "index.html")));
                        assertTrue(Files.exists(Path.of(root, "study-logs", f.uuid + ".log")));
                        switch (outcome) {
                            case EXCEPTION -> throw new IllegalStateException("intentional rollback");
                            case ROLLBACK_ONLY -> em.getTransaction().setRollbackOnly();
                            case COMMIT_FAILURE ->
                                    em.unwrap(Session.class).getTransaction().registerSynchronization(new Synchronization() {
                                        @Override
                                        public void beforeCompletion() {
                                            throw new IllegalStateException("intentional commit failure");
                                        }

                                        @Override
                                        public void afterCompletion(int status) {
                                        }
                                    });
                            default -> {}
                        }
                    });
                    if (outcome == Outcome.EXCEPTION || outcome == Outcome.COMMIT_FAILURE) {
                        RuntimeException failure = assertThrows(RuntimeException.class, deletion::run);
                        assertTrue(failure.toString(), hasMessage(failure, "intentional"));
                    } else deletion.run();
                    boolean committed = outcome == Outcome.COMMIT;
                    if (!committed)
                        assertEquals(logBefore, Files.readString(Path.of(root, "study-logs", f.uuid + ".log")));
                    assertRows(jpa, f.selected, !committed);
                    assertRows(jpa, f.siblingRuns, !(committed && wholeStudy));
                    assertRows(jpa, f.otherRuns, true);
                    assertFiles(f.selected, !committed);
                    assertFiles(f.siblingRuns, !(committed && wholeStudy));
                    assertFiles(f.otherRuns, true);
                    assertEntity(jpa, Study.class, f.study, !(committed && wholeStudy));
                    assertEntity(jpa, Batch.class, f.batch, !committed);
                    assertEntity(jpa, Batch.class, f.sibling, !(committed && wholeStudy));
                    assertEntity(jpa, Study.class, f.otherStudy, true);
                    assertEntity(jpa, PersonalMultipleWorker.class, f.worker, true);
                    assertEquals(!(committed && wholeStudy), Files.exists(Path.of(root, "assets", f.dir)));
                    assertEquals(!(committed && wholeStudy), Files.exists(Path.of(root, "study-logs", f.uuid + ".log")));
                    long links = jpa.withTransaction(em -> {
                        return em.createQuery("select count(l) from StudyLink l where l.batch.id = :id", Long.class)
                                .setParameter("id", f.batch).getSingleResult();
                    });
                    assertEquals(committed ? 0 : 4, links);
                }
            }
            // A filesystem error must not turn a committed database deletion into a failed transaction or prevent
            // other callbacks from cleaning uploads. Leave a regular file where a study directory is expected.
            Fixture f = fixture(jpa, app);
            Path assets = Path.of(root, "assets", f.dir);
            Files.delete(assets.resolve("index.html"));
            Files.delete(assets);
            Files.writeString(assets, "intentional cleanup failure");
            jpa.withTransaction((EntityManager em) -> studies.removeStudyInclAssets(em.find(Study.class, f.study)));
            assertEntity(jpa, Study.class, f.study, false);
            assertFiles(f.selected, false);
            assertTrue(Files.isRegularFile(assets));
            AtomicBoolean laterCallback = new AtomicBoolean();
            jpa.withTransaction((EntityManager em) -> {
                app.injector().instanceOf(StudyDao.class).afterCommit("intentional test failure", () -> {
                    throw new java.io.IOException("test");
                });
                app.injector().instanceOf(StudyDao.class).afterCommit("subsequent callback", () -> laterCallback.set(true));
            });
            assertTrue(laterCallback.get());
        } finally {
            Context.clear();
            Helpers.stop(app);
        }
    }

    private Fixture fixture(JPAApi jpa, Application app) throws Exception {
        Fixture f = jpa.withTransaction(em -> {
            PersonalMultipleWorker worker = new PersonalMultipleWorker();
            em.persist(worker);
            Study study = study(em, "deletion");
            Batch selected = batch(em, study, worker);
            Batch sibling = batch(em, study, worker);
            Study other = study(em, "unrelated");
            Batch otherBatch = batch(em, other, worker);
            List<Run> selectedRuns = runs(em, study, selected, worker);
            List<Run> siblingRuns = runs(em, study, sibling, worker);
            List<Run> otherRuns = runs(em, other, otherBatch, worker);
            em.flush();
            app.injector().instanceOf(StudyLogger.class).create(study);
            return new Fixture(study.getId(), selected.getId(), sibling.getId(), other.getId(), worker.getId(),
                    study.getDirName(), study.getUuid(), selectedRuns, siblingRuns, otherRuns);
        });
        Path assets = Path.of(temp.getRoot().getAbsolutePath(), "assets", f.dir);
        Files.createDirectories(assets);
        Files.writeString(assets.resolve("index.html"), "study assets");
        for (List<Run> runs : List.of(f.selected, f.siblingRuns, f.otherRuns)) {
            for (Run run : runs) {
                Path dir = IOUtils.getResultUploadsDir(run.result, run.component);
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("result.txt"), "uploaded result");
            }
        }
        return f;
    }

    private Study study(EntityManager em, String title) {
        Study study = new Study();
        study.setTitle(title);
        study.setGroupStudy(true);
        em.persist(study);
        Component component = new Component();
        component.setTitle("component");
        component.setHtmlFilePath("index.html");
        component.setStudy(study);
        study.addComponent(component);
        em.persist(component);
        return study;
    }

    private Batch batch(EntityManager em, Study study, PersonalMultipleWorker worker) {
        Batch batch = new Batch();
        batch.setTitle("batch");
        batch.setUuid(UUID.randomUUID().toString());
        study.addBatch(batch);
        batch.addWorker(worker);
        worker.addBatch(batch);
        em.persist(batch);
        return batch;
    }

    private List<Run> runs(EntityManager em, Study study, Batch batch, PersonalMultipleWorker worker) {
        List<Run> runs = new ArrayList<>();
        for (boolean historical : new boolean[]{false, true}) {
            GroupResult group = new GroupResult(batch);
            group.setGroupState(historical ? GroupResult.GroupState.FINISHED : GroupResult.GroupState.STARTED);
            em.persist(group);
            // Multiple members per group reproduce the original history case, not just isolated references.
            for (int member = 0; member < 2; member++) {
                StudyLink link = new StudyLink(batch, worker);
                em.persist(link);
                StudyResult result = new StudyResult(link, worker);
                if (historical) {
                    result.setHistoryGroupResult(group);
                    result.setStudyState(StudyResult.StudyState.FINISHED);
                    group.addHistoryMember(result);
                } else {
                    result.setActiveGroupResult(group);
                    group.addActiveMember(result);
                }
                em.persist(result);
                worker.addStudyResult(result);
                ComponentResult cr = new ComponentResult(study.getComponentList().getFirst());
                cr.setStudyResult(result);
                result.addComponentResult(cr);
                em.persist(cr);
                runs.add(new Run(result.getId(), cr.getId(), group.getId(), historical));
            }
        }
        return runs;
    }

    private void assertRows(JPAApi jpa, List<Run> runs, boolean present) {
        for (Run run : runs) {
            assertEntity(jpa, StudyResult.class, run.result, present);
            assertEntity(jpa, ComponentResult.class, run.component, present);
            assertEntity(jpa, GroupResult.class, run.group, present);
            if (present) jpa.withTransaction((EntityManager em) -> {
                StudyResult result = em.find(StudyResult.class, run.result);
                assertEquals(run.group, (run.historical ? result.getHistoryGroupResult() : result.getActiveGroupResult()).getId());
            });
        }
    }

    private void assertEntity(JPAApi jpa, Class<?> type, Long id, boolean present) {
        boolean found = jpa.withTransaction(em -> em.find(type, id) != null);
        assertEquals(type.getSimpleName() + " " + id, present, found);
    }

    private boolean hasMessage(Throwable error, String text) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(text)) return true;
        }
        return false;
    }

    private void assertFiles(List<Run> runs, boolean present) {
        for (Run run : runs)
            assertEquals("Uploads " + run.result, present,
                    Files.exists(IOUtils.getResultUploadsDir(run.result, run.component).resolve("result.txt")));
    }
}
