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
import models.common.workers.PersonalSingleWorker;
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
public class PersonalSingleStudyAuthorisationTest {

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
	}

	@Test
	public void checkWorkerAllowedToStartStudy()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Batch batch = study.getDefaultBatch();
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);

		PersonalSingleWorker worker = new PersonalSingleWorker();
		jpaApi.withTransaction(() -> {
			workerDao.create(worker);
		});

		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);
	}

	@Test
	public void checkWorkerAllowedToStartStudyFail()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Batch batch = study.getDefaultBatch();
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);

		PersonalSingleWorker worker = new PersonalSingleWorker();
		jpaApi.withTransaction(() -> {
			workerDao.create(worker);
		});

		// Doesn't start if there is an StudyResult already
		createStudyResult(study, batch, worker, StudyState.FINISHED);

		try {
			studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Batch batch = study.getDefaultBatch();
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);

		PersonalSingleWorker worker = new PersonalSingleWorker();
		jpaApi.withTransaction(() -> {
			workerDao.create(worker);
		});

		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);
	}

	@Test
	public void checkWorkerAllowedToWrongWorkerType()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Batch batch = study.getDefaultBatch();
		batch.removeAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);

		PersonalSingleWorker worker = new PersonalSingleWorker();
		jpaApi.withTransaction(() -> {
			workerDao.create(worker);
		});

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
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);

		PersonalSingleWorker worker = new PersonalSingleWorker();
		jpaApi.withTransaction(() -> {
			workerDao.create(worker);
		});

		// PersonalSingleWorker cannot repeat the same study (StudyState in
		// FINISHED, FAIL, ABORTED
		createStudyResult(study, batch, worker, StudyState.FINISHED);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		createStudyResult(study, batch, worker, StudyState.FAIL);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		createStudyResult(study, batch, worker, StudyState.ABORTED);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

	private StudyResult createStudyResult(Study study, Batch batch,
			PersonalSingleWorker worker, StudyState studyState) {
		return jpaApi.withTransaction(() -> {
			StudyResult studyResult = resultCreator.createStudyResult(study,
					batch, worker);
			studyResult.setStudyState(studyState);
			return studyResult;
		});
	}

}
