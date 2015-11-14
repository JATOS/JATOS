package publix.services.personal_single;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.common.Study;
import models.common.StudyResult.StudyState;
import models.common.workers.PersonalSingleWorker;

import org.fest.assertions.Fail;
import org.junit.Test;

import publix.AbstractTest;
import publix.PublixTestGlobal;
import services.publix.PublixErrorMessages;
import services.publix.personal_single.PersonalSingleErrorMessages;
import services.publix.personal_single.PersonalSingleStudyAuthorisation;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;

/**
 * @author Kristian Lange
 */
public class PersonalSingleStudyAuthorisationTest extends AbstractTest {

	private PersonalSingleErrorMessages personalSingleErrorMessages;
	private PersonalSingleStudyAuthorisation studyAuthorisation;

	@Override
	public void before() throws Exception {
		personalSingleErrorMessages = PublixTestGlobal.INJECTOR
				.getInstance(PersonalSingleErrorMessages.class);
		studyAuthorisation = PublixTestGlobal.INJECTOR
				.getInstance(PersonalSingleStudyAuthorisation.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void checkWorkerAllowedToStartStudy()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = importExampleStudy();
		study.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalSingleWorker worker = new PersonalSingleWorker();
		addWorker(worker);

		studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToStartStudyFail()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = importExampleStudy();
		study.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalSingleWorker worker = new PersonalSingleWorker();
		addWorker(worker);

		// Doesn't start if there is an StudyResult already
		addStudyResult(study, worker, StudyState.FINISHED);

		try {
			studyAuthorisation.checkWorkerAllowedToStartStudy(worker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		Study study = importExampleStudy();
		study.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalSingleWorker worker = new PersonalSingleWorker();
		addWorker(worker);

		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToWrongWorkerType()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = importExampleStudy();
		study.removeAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalSingleWorker worker = new PersonalSingleWorker();
		addWorker(worker);

		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					personalSingleErrorMessages.workerTypeNotAllowed(worker
							.getUIWorkerType()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyFinishedStudy()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = importExampleStudy();
		study.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalSingleWorker worker = new PersonalSingleWorker();
		addWorker(worker);

		// PersonalSingleWorker cannot repeat the same study (StudyState in
		// FINISHED, FAIL, ABORTED
		addStudyResult(study, worker, StudyState.FINISHED);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		addStudyResult(study, worker, StudyState.FAIL);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		addStudyResult(study, worker, StudyState.ABORTED);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}

		// Clean-up
		removeStudy(study);
	}

}
