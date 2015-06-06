package publix;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.StudyModel;
import models.StudyResult.StudyState;
import models.workers.PersonalSingleWorker;

import org.fest.assertions.Fail;
import org.junit.Test;

import publix.controllers.personal_single.PersonalSingleErrorMessages;
import publix.controllers.personal_single.PersonalSinglePublixUtils;
import publix.controllers.personal_single.PersonalSingleStudyAuthorisation;
import publix.exceptions.ForbiddenPublixException;
import publix.exceptions.PublixException;
import publix.services.PublixErrorMessages;
import common.Global;

/**
 * @author Kristian Lange
 */
public class PersonalSinglePublixUtilsTest extends
		PublixUtilsTest<PersonalSingleWorker> {

	private PersonalSingleErrorMessages personalSingleErrorMessages;
	private PersonalSinglePublixUtils personalSinglePublixUtils;
	private PersonalSingleStudyAuthorisation studyAuthorisation;

	@Override
	public void before() throws Exception {
		super.before();
		personalSinglePublixUtils = Global.INJECTOR
				.getInstance(PersonalSinglePublixUtils.class);
		publixUtils = personalSinglePublixUtils;
		personalSingleErrorMessages = Global.INJECTOR
				.getInstance(PersonalSingleErrorMessages.class);
		errorMessages = personalSingleErrorMessages;
		studyAuthorisation = Global.INJECTOR
				.getInstance(PersonalSingleStudyAuthorisation.class);
	}

	@Override
	public void after() throws Exception {
		super.before();
	}

	@Test
	public void checkRetrieveTypedWorker() throws NoSuchAlgorithmException,
			IOException, PublixException {
		PersonalSingleWorker worker = new PersonalSingleWorker();
		addWorker(worker);

		PersonalSingleWorker retrievedWorker = publixUtils
				.retrieveTypedWorker(worker.getId().toString());
		assertThat(retrievedWorker.getId()).isEqualTo(worker.getId());
	}

	@Test
	public void checkRetrieveTypedWorkerWrongType()
			throws NoSuchAlgorithmException, IOException, PublixException {
		try {
			publixUtils.retrieveTypedWorker(admin.getWorker().getId()
					.toString());
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					personalSingleErrorMessages.workerNotCorrectType(admin
							.getWorker().getId()));
		}
	}

	@Test
	public void checkWorkerAllowedToStartStudy()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		study.addAllowedWorker(PersonalSingleWorker.WORKER_TYPE);
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
		StudyModel study = importExampleStudy();
		study.addAllowedWorker(PersonalSingleWorker.WORKER_TYPE);
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
		StudyModel study = importExampleStudy();
		study.addAllowedWorker(PersonalSingleWorker.WORKER_TYPE);
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
		StudyModel study = importExampleStudy();
		study.removeAllowedWorker(PersonalSingleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalSingleWorker worker = new PersonalSingleWorker();
		addWorker(worker);

		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(
							errorMessages.workerTypeNotAllowed(worker
									.getUIWorkerType()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyFinishedStudy()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		study.addAllowedWorker(PersonalSingleWorker.WORKER_TYPE);
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
