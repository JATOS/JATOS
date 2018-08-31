package services.publix.workers;

import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import general.TestHelper;
import models.common.Batch;
import models.common.Study;
import models.common.StudyResult;
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
import services.publix.PublixErrorMessages;
import services.publix.ResultCreator;

import javax.inject.Inject;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Kristian Lange
 */
public class MTStudyAuthorisationTest {

    private Injector injector;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private WorkerDao workerDao;

    @Inject
    private MTStudyAuthorisation studyAuthorisation;

    @Inject
    private ResultCreator resultCreator;

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
    public void checkWorkerAllowedToStartStudy()
            throws ForbiddenPublixException {
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

        studyAuthorisation.checkWorkerAllowedToStartStudy(mtWorker, study, batch);
        studyAuthorisation.checkWorkerAllowedToStartStudy(mtSandboxWorker, study, batch);
    }

    @Test
    public void checkWorkerAllowedToStartStudyDidStudyAlready()
            throws ForbiddenPublixException {
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

        // MTWorker is not allowed to start an already started study regardless
        // of the StudyState
        createStudyResult(study, batch, mtWorker, StudyState.STARTED);
        try {
            studyAuthorisation.checkWorkerAllowedToStartStudy(mtWorker, study,
                    batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }

        // MTSandboxWorker is allowed to start again
        studyAuthorisation.checkWorkerAllowedToStartStudy(mtSandboxWorker, study, batch);
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

        createStudyResult(study, batch, mtWorker, StudyState.STARTED);

        studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study, batch);
        studyAuthorisation.checkWorkerAllowedToDoStudy(mtSandboxWorker, study, batch);
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

        // Study doesn't allow this worker type
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                    .workerTypeNotAllowed(mtWorker.getUIWorkerType(), study.getId(),
                            batch.getId()));
        }

        // Study doesn't allow this worker type
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(mtSandboxWorker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                    .workerTypeNotAllowed(mtSandboxWorker.getUIWorkerType(), study.getId(),
                            batch.getId()));
        }
    }

    @Test
    public void checkWorkerAllowedToDoStudyMoreThanOnce()
            throws ForbiddenPublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);

        MTWorker mtWorker = new MTWorker();
        MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
        jpaApi.withTransaction(() -> {
            workerDao.create(mtWorker);
            workerDao.create(mtSandboxWorker);
        });
        batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);
        batch.removeAllowedWorkerType(MTSandboxWorker.WORKER_TYPE);

        // MTWorkers cannot repeat the same study (StudyState in FINISHED, FAIL,
        // ABORTED
        createStudyResult(study, batch, mtWorker, StudyState.FINISHED);
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }
        createStudyResult(study, batch, mtWorker, StudyState.FAIL);
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study,
                    batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }
        createStudyResult(study, batch, mtWorker, StudyState.ABORTED);
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study,
                    batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }

        // MTSandboxWorkers can repeat the same study
        studyAuthorisation.checkWorkerAllowedToDoStudy(mtSandboxWorker, study, batch);
    }

    private StudyResult createStudyResult(Study study, Batch batch, MTWorker mtWorker,
            StudyState studyState) {
        return jpaApi.withTransaction(() -> {
            StudyResult studyResult = resultCreator.createStudyResult(study, batch, mtWorker);
            studyResult.setStudyState(studyState);
            return studyResult;
        });
    }

}
