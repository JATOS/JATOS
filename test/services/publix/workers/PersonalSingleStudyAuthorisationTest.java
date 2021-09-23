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
import models.common.workers.PersonalSingleWorker;
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
public class PersonalSingleStudyAuthorisationTest {

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
    private PersonalSingleStudyAuthorisation studyAuthorisation;

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
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        PersonalSingleWorker worker = new PersonalSingleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        studyAuthorisation.checkWorkerAllowedToStartStudy(session, worker, study, batch);
    }

    @Test
    public void checkWorkerAllowedToStartStudyFail() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        PersonalSingleWorker worker = new PersonalSingleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        // Doesn't start if there is an StudyResult already
        resultTestHelper.createStudyResult(batch, worker, StudyState.FINISHED);

        try {
            studyAuthorisation.checkWorkerAllowedToStartStudy(session, worker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }
    }

    @Test
    public void checkWorkerAllowedToStartStudyPre() throws ForbiddenPublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        PersonalSingleWorker worker = new PersonalSingleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        // Does start if there is an StudyResult which is in state PRE
        resultTestHelper.createStudyResult(batch, worker, StudyState.PRE);

        studyAuthorisation.checkWorkerAllowedToStartStudy(session, worker, study, batch);
    }

    @Test
    public void checkWorkerAllowedToDoStudy() throws ForbiddenPublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        PersonalSingleWorker worker = new PersonalSingleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        studyAuthorisation.checkWorkerAllowedToDoStudy(session, worker, study, batch);
    }

    @Test
    public void checkWorkerAllowedToWrongWorkerType() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.removeAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        PersonalSingleWorker worker = new PersonalSingleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(session, worker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages.workerTypeNotAllowed(
                    worker.getUIWorkerType(), study.getId(), batch.getId()));
        }
    }

    @Test
    public void checkWorkerAllowedToDoStudyFinishedStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        PersonalSingleWorker worker = new PersonalSingleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        // PersonalSingleWorker cannot repeat the same study (StudyState in
        // FINISHED, FAIL, ABORTED
        resultTestHelper.createStudyResult(batch, worker, StudyState.FINISHED);
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(session, worker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }
        resultTestHelper.createStudyResult(batch, worker, StudyState.FAIL);
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(session, worker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }
        resultTestHelper.createStudyResult(batch, worker, StudyState.ABORTED);
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(session, worker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }
    }

}
