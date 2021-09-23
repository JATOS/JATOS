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
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.GeneralSingleWorker;
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
public class GeneralSingleStudyAuthorisationTest {

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
    private GeneralSingleStudyAuthorisation studyAuthorisation;

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
    public void checkWorkerAllowedToDoStudy() throws ForbiddenPublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(GeneralSingleWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        GeneralSingleWorker worker = new GeneralSingleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = resultTestHelper.createStudyResult(batch, worker, StudyState.STARTED);
            studyResult.setStudyState(StudyState.STARTED);
        });

        studyAuthorisation.checkWorkerAllowedToDoStudy(session, worker, study, batch);
    }

    @Test
    public void checkWorkerAllowedToStartStudyPre() throws ForbiddenPublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(GeneralSingleWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        GeneralSingleWorker worker = new GeneralSingleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        // Does start if there is an StudyResult which is in state PRE
        resultTestHelper.createStudyResult(batch, worker, StudyState.PRE);

        studyAuthorisation.checkWorkerAllowedToStartStudy(session, worker, study, batch);
    }

    @Test
    public void checkWorkerAllowedToDoStudyWrongWorkerType() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        GeneralSingleWorker worker = new GeneralSingleWorker();
        Http.Session session = new Http.Session(new HashMap<>());

        // Study doesn't allow this worker type
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(session, worker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                    .workerTypeNotAllowed(worker.getUIWorkerType(), study.getId(), batch.getId()));
        }
    }

    @Test
    public void checkWorkerAllowedToDoStudyFinishedStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        study.getDefaultBatch().addAllowedWorkerType(GeneralSingleWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        GeneralSingleWorker worker = new GeneralSingleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = resultTestHelper.createStudyResult(batch, worker, StudyState.STARTED);
            studyResult.setStudyState(StudyState.FINISHED);
        });

        // General single workers can't repeat the same study
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(session, worker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }
    }

}
