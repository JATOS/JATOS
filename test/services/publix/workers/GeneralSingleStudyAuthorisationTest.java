package services.publix.workers;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;

import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
import models.common.workers.GeneralSingleWorker;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import services.publix.PublixErrorMessages;
import services.publix.ResultCreator;

/**
 * @author Kristian Lange
 */
public class GeneralSingleStudyAuthorisationTest {

    private Injector injector;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private WorkerDao workerDao;

    @Inject
    private ResultCreator resultCreator;

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
    public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
            IOException, ForbiddenPublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(GeneralSingleWorker.WORKER_TYPE);

        GeneralSingleWorker worker = new GeneralSingleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = resultCreator.createStudyResult(study,
                    batch, worker);
            studyResult.setStudyState(StudyState.STARTED);
        });

        studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
    }

    @Test
    public void checkWorkerAllowedToDoStudyWrongWorkerType()
            throws NoSuchAlgorithmException, IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Batch batch = study.getDefaultBatch();
        GeneralSingleWorker worker = new GeneralSingleWorker();

        // Study doesn't allow this worker type
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study,
                    batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                    .workerTypeNotAllowed(worker.getUIWorkerType(),
                            study.getId(), batch.getId()));
        }
    }

    @Test
    public void checkWorkerAllowedToDoStudyFinishedStudy()
            throws NoSuchAlgorithmException, IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Batch batch = study.getDefaultBatch();
        study.getDefaultBatch()
                .addAllowedWorkerType(GeneralSingleWorker.WORKER_TYPE);

        GeneralSingleWorker worker = new GeneralSingleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = resultCreator.createStudyResult(study,
                    batch, worker);
            studyResult.setStudyState(StudyState.FINISHED);
        });

        // General single workers can't repeat the same study
        try {
            studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study,
                    batch);
            Fail.fail();
        } catch (PublixException e) {
            assertThat(e.getMessage())
                    .isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }
    }

}
