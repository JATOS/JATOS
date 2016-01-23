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
import models.common.workers.PersonalSingleWorker;
import services.publix.PublixErrorMessages;
import services.publix.workers.PersonalSingleErrorMessages;
import services.publix.workers.PersonalSingleStudyAuthorisation;

/**
 * @author Kristian Lange
 */
public class PersonalSingleStudyAuthorisationTest extends AbstractTest {

	private PersonalSingleErrorMessages personalSingleErrorMessages;
	private PersonalSingleStudyAuthorisation studyAuthorisation;

	@Override
	public void before() throws Exception {
		personalSingleErrorMessages = application.injector()
				.instanceOf(PersonalSingleErrorMessages.class);
		studyAuthorisation = application.injector()
				.instanceOf(PersonalSingleStudyAuthorisation.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void checkWorkerAllowedToStartStudy()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalSingleWorker worker = new PersonalSingleWorker();
		persistWorker(worker);

		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study, batch);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToStartStudyFail()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalSingleWorker worker = new PersonalSingleWorker();
		persistWorker(worker);

		// Doesn't start if there is an StudyResult already
		addStudyResult(study, batch, worker, StudyState.FINISHED);

		try {
			studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalSingleWorker worker = new PersonalSingleWorker();
		persistWorker(worker);

		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToWrongWorkerType()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		batch.removeAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalSingleWorker worker = new PersonalSingleWorker();
		persistWorker(worker);

		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(personalSingleErrorMessages
					.workerTypeNotAllowed(worker.getUIWorkerType(),
							study.getId(), batch.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyFinishedStudy()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalSingleWorker worker = new PersonalSingleWorker();
		persistWorker(worker);

		// PersonalSingleWorker cannot repeat the same study (StudyState in
		// FINISHED, FAIL, ABORTED
		addStudyResult(study, batch, worker, StudyState.FINISHED);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		addStudyResult(study, batch, worker, StudyState.FAIL);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		addStudyResult(study, batch, worker, StudyState.ABORTED);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}

		// Clean-up
		removeStudy(study);
	}

}
