package publix.services.workers;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import general.AbstractTest;
import models.common.Batch;
import models.common.Study;
import models.common.StudyResult.StudyState;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import services.publix.PublixErrorMessages;
import services.publix.workers.MTErrorMessages;
import services.publix.workers.MTStudyAuthorisation;

/**
 * @author Kristian Lange
 */
public class MTStudyAuthorisationTest extends AbstractTest {

	private MTErrorMessages mtErrorMessages;
	private MTStudyAuthorisation studyAuthorisation;

	@Override
	public void before() throws Exception {
		mtErrorMessages = application.injector()
				.instanceOf(MTErrorMessages.class);
		studyAuthorisation = application.injector()
				.instanceOf(MTStudyAuthorisation.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void checkWorkerAllowedToStartStudy()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		// Check both MTWorker and MTSandboxWorker
		MTWorker mtWorker = new MTWorker();
		persistWorker(mtWorker);
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		persistWorker(mtSandboxWorker);

		// It's enough to allow MTWorker to allow both MTWorker and also
		// MTSandboxWorker
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);
		addStudy(study);

		studyAuthorisation.checkWorkerAllowedToStartStudy(mtWorker, study,
				batch);
		studyAuthorisation.checkWorkerAllowedToStartStudy(mtSandboxWorker,
				study, batch);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToStartStudyDidStudyAlready()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		// Check both MTWorker and MTSandboxWorker
		MTWorker mtWorker = new MTWorker();
		persistWorker(mtWorker);
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		persistWorker(mtSandboxWorker);

		// It's enough to allow MTWorker to allow both MTWorker and also
		// MTSandboxWorker
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);
		addStudy(study);

		// MTWorker is not allowed to start an already started study regardless
		// of the StudyState
		addStudyResult(study, batch, mtWorker, StudyState.STARTED);
		try {
			studyAuthorisation.checkWorkerAllowedToStartStudy(mtWorker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}

		// MTSandboxWorker is allowed to start again
		studyAuthorisation.checkWorkerAllowedToStartStudy(mtSandboxWorker,
				study, batch);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		// Check both MTWorker and MTSandboxWorker
		MTWorker mtWorker = new MTWorker();
		persistWorker(mtWorker);
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		persistWorker(mtSandboxWorker);

		// It's enough to allow MTWorker to allow both MTWorker and also
		// MTSandboxWorker
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);
		addStudy(study);

		addStudyResult(study, batch, mtWorker, StudyState.STARTED);

		studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study, batch);
		studyAuthorisation.checkWorkerAllowedToDoStudy(mtSandboxWorker, study,
				batch);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyWrongWorkerType()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		Batch batch = study.getDefaultBatch();
		// Check both MTWorker and MTSandboxWorker
		MTWorker mtWorker = new MTWorker();
		persistWorker(mtWorker);
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		persistWorker(mtSandboxWorker);
		batch.removeAllowedWorkerType(MTWorker.WORKER_TYPE);
		batch.removeAllowedWorkerType(MTSandboxWorker.WORKER_TYPE);

		// Study doesn't allow this worker type
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(mtErrorMessages
					.workerTypeNotAllowed(mtWorker.getUIWorkerType(),
							study.getId(), batch.getId()));
		}

		// Study doesn't allow this worker type
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(mtSandboxWorker,
					study, batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(mtErrorMessages
					.workerTypeNotAllowed(mtSandboxWorker.getUIWorkerType(),
							study.getId(), batch.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyMoreThanOnce()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		MTWorker mtWorker = new MTWorker();
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		persistWorker(mtWorker);
		batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);
		batch.removeAllowedWorkerType(MTSandboxWorker.WORKER_TYPE);
		addStudy(study);

		// MTWorkers cannot repeat the same study (StudyState in FINISHED, FAIL,
		// ABORTED
		addStudyResult(study, batch, mtWorker, StudyState.FINISHED);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		addStudyResult(study, batch, mtWorker, StudyState.FAIL);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		addStudyResult(study, batch, mtWorker, StudyState.ABORTED);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}

		// MTSandboxWorkers can repeat the same study
		studyAuthorisation.checkWorkerAllowedToDoStudy(mtSandboxWorker, study,
				batch);

		// Clean-up
		removeStudy(study);
	}

}
