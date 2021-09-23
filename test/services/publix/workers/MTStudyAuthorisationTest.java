package services.publix.workers;

import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import general.ResultTestHelper;
import general.TestHelper;
import models.common.Batch;
import models.common.Study;
import models.common.StudyResult.StudyState;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Http;
import services.publix.PublixErrorMessages;

import javax.inject.Inject;
import java.util.HashMap;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
public class MTStudyAuthorisationTest {

    private Injector injector;

    @Inject
    private TestHelper testHelper;

    @Inject
    private ResultTestHelper resultTestHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private WorkerDao workerDao;

    @Inject
    private MTStudyAuthorisation studyAuthorisation;

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
    public void checkWorkerAllowedToStartStudy() throws ForbiddenPublixException {
        // Check both MTWorker and MTSandboxWorker
        MTWorker mtWorker = new MTWorker();
        MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
        jpaApi.withTransaction(() -> {
            workerDao.create(mtWorker);
            workerDao.create(mtSandboxWorker);
        });

        // It's enough to allow MTWorker to allow both MTWorker and also MTSandboxWorker
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        studyAuthorisation.checkWorkerAllowedToStartStudy(session, mtWorker, study, batch);
        studyAuthorisation.checkWorkerAllowedToStartStudy(session, mtSandboxWorker, study, batch);
    }

    @Test
    public void checkWorkerAllowedToStartStudyDidStudyAlready() throws ForbiddenPublixException {
        // Check both MTWorker and MTSandboxWorker
        MTWorker mtWorker = new MTWorker();
        MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
        jpaApi.withTransaction(() -> {
            workerDao.create(mtWorker);
            workerDao.create(mtSandboxWorker);
        });

        // It's enough to allow MTWorker to allow both MTWorker and also MTSandboxWorker
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        // MTWorker and MTSandboxWorker are allowed to start again
        studyAuthorisation.checkWorkerAllowedToStartStudy(session, mtWorker, study, batch);
        studyAuthorisation.checkWorkerAllowedToStartStudy(session, mtSandboxWorker, study, batch);
    }

    @Test
    public void checkWorkerAllowedToDoStudy() throws ForbiddenPublixException {
        // Check both MTWorker and MTSandboxWorker
        MTWorker mtWorker = new MTWorker();
        MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
        jpaApi.withTransaction(() -> {
            workerDao.create(mtWorker);
            workerDao.create(mtSandboxWorker);
        });

        // It's enough to allow MTWorker to allow both MTWorker and also
        // MTSandboxWorker
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        resultTestHelper.createStudyResult(batch, mtWorker, StudyState.STARTED);

        studyAuthorisation.checkWorkerAllowedToDoStudy(session, mtWorker, study, batch);
        studyAuthorisation.checkWorkerAllowedToDoStudy(session, mtSandboxWorker, study, batch);
    }

    @Test
    public void checkWorkerAllowedToDoStudyWrongWorkerType() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);

        // Check both MTWorker and MTSandboxWorker
        MTWorker mtWorker = new MTWorker();
        MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
        jpaApi.withTransaction(() -> {
            workerDao.create(mtWorker);
            workerDao.create(mtSandboxWorker);
        });
        batch.removeAllowedWorkerType(MTWorker.WORKER_TYPE);
        batch.removeAllowedWorkerType(MTSandboxWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        // Study doesn't allow this worker type
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(session, mtWorker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                    .workerTypeNotAllowed(mtWorker.getUIWorkerType(), study.getId(),
                            batch.getId()));
        }

        // Study doesn't allow this worker type
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(session, mtSandboxWorker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                    .workerTypeNotAllowed(mtSandboxWorker.getUIWorkerType(), study.getId(),
                            batch.getId()));
        }
    }

    @Test
    public void checkWorkerAllowedToDoStudyMoreThanOnce() throws ForbiddenPublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        MTWorker mtWorker = new MTWorker();
        MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
        jpaApi.withTransaction(() -> {
            workerDao.create(mtWorker);
            workerDao.create(mtSandboxWorker);
        });
        batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);

        // MTWorker and MTSandboxWorkers can repeat the same study
        studyAuthorisation.checkWorkerAllowedToDoStudy(session, mtWorker, study, batch);
        studyAuthorisation.checkWorkerAllowedToDoStudy(session, mtSandboxWorker, study, batch);
    }

}
