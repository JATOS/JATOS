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
import services.publix.PublixErrorMessages;
import services.publix.ResultCreator;

import javax.inject.Inject;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Kristian Lange
 */
public class PersonalMultipleStudyAuthorisationTest {

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
    private PersonalMultipleStudyAuthorisation studyAuthorisation;

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
        batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);

        PersonalMultipleWorker worker = new PersonalMultipleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
    }

    @Test
    public void checkWorkerAllowedToWrongWorkerType()
            throws NoSuchAlgorithmException, IOException,
            ForbiddenPublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.removeAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);

        PersonalMultipleWorker worker = new PersonalMultipleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

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
            throws NoSuchAlgorithmException, IOException,
            ForbiddenPublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);

        PersonalMultipleWorker worker = new PersonalMultipleWorker();
        jpaApi.withTransaction(() -> workerDao.create(worker));

        // State of study has no influence. Personal multiple workers can do
        // studies multiple times (we create a StudyResult just in case)
        createStudyResult(study, batch, worker, StudyState.FINISHED);
        studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
    }

    private StudyResult createStudyResult(Study study, Batch batch,
            PersonalMultipleWorker worker, StudyState studyState) {
        return jpaApi.withTransaction(() -> {
            StudyResult studyResult = resultCreator.createStudyResult(study,
                    batch, worker);
            studyResult.setStudyState(studyState);
            return studyResult;
        });
    }

}
