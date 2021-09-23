package services.publix.workers;

import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import general.TestHelper;
import models.common.Batch;
import models.common.Study;
import models.common.StudyLink;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.GeneralMultipleWorker;
import models.common.workers.JatosWorker;
import models.common.workers.PersonalMultipleWorker;
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
import general.ResultTestHelper;
import services.publix.PublixErrorMessages;
import services.publix.ResultCreator;

import javax.inject.Inject;
import java.util.HashMap;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
public class GeneralMultipleStudyAuthorisationTest {

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
    private ResultCreator resultCreator;

    @Inject
    private GeneralMultipleStudyAuthorisation studyAuthorisation;

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
        batch.addAllowedWorkerType(GeneralMultipleWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        GeneralMultipleWorker worker = new GeneralMultipleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        studyAuthorisation.checkWorkerAllowedToDoStudy(session, worker, study, batch);
    }

    @Test
    public void checkWorkerAllowedToWrongWorkerType() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.removeAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        GeneralMultipleWorker worker = new GeneralMultipleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(session, worker, study, batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                    .workerTypeNotAllowed(worker.getUIWorkerType(), study.getId(), batch.getId()));
        }
    }

    @Test
    public void checkWorkerAllowedToDoStudyFinishedStudy() throws ForbiddenPublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(GeneralMultipleWorker.WORKER_TYPE);
        Http.Session session = new Http.Session(new HashMap<>());

        GeneralMultipleWorker worker = new GeneralMultipleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        // State of study has no influence. General multiple workers can do
        // studies multiple times (we create a StudyResult just in case)
        resultTestHelper.createStudyResult(batch, worker, StudyState.FINISHED);
        studyAuthorisation.checkWorkerAllowedToDoStudy(session, worker, study, batch);
    }

}
