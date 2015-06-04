package publix;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.StudyModel;
import models.StudyResult.StudyState;
import models.workers.GeneralSingleWorker;
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;

import org.fest.assertions.Fail;
import org.junit.Test;

import common.Global;

import controllers.publix.PublixErrorMessages;
import controllers.publix.mt.MTErrorMessages;
import controllers.publix.mt.MTPublixUtils;
import controllers.publix.mt.MTStudyAuthorisation;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;

/**
 * @author Kristian Lange
 */
public class MTPublixUtilsTest extends PublixUtilsTest<MTWorker> {

	private MTErrorMessages mtErrorMessages;
	private MTPublixUtils mtPublixUtils;
	private MTStudyAuthorisation studyAuthorisation;

	@Override
	public void before() throws Exception {
		super.before();
		mtPublixUtils = Global.INJECTOR.getInstance(MTPublixUtils.class);
		publixUtils = mtPublixUtils;
		mtErrorMessages = Global.INJECTOR.getInstance(MTErrorMessages.class);
		errorMessages = mtErrorMessages;
		studyAuthorisation = Global.INJECTOR
				.getInstance(MTStudyAuthorisation.class);
	}

	@Override
	public void after() throws Exception {
		super.before();
	}

	@Test
	public void checkRetrieveTypedWorker() throws NoSuchAlgorithmException,
			IOException, PublixException {
		MTWorker mtWorker = new MTWorker();
		addWorker(mtWorker);
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		addWorker(mtSandboxWorker);

		MTWorker retrievedWorker = publixUtils.retrieveTypedWorker(mtWorker
				.getId().toString());
		assertThat(retrievedWorker.getId()).isEqualTo(mtWorker.getId());
		retrievedWorker = publixUtils.retrieveTypedWorker(mtSandboxWorker
				.getId().toString());
		assertThat(retrievedWorker.getId()).isEqualTo(mtSandboxWorker.getId());

	}

	@Test
	public void checkRetrieveTypedWorkerWrongType()
			throws NoSuchAlgorithmException, IOException, PublixException {
		GeneralSingleWorker generalSingleWorker = new GeneralSingleWorker();
		addWorker(generalSingleWorker);

		try {
			publixUtils.retrieveTypedWorker(generalSingleWorker.getId()
					.toString());
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					mtErrorMessages.workerNotCorrectType(generalSingleWorker
							.getId()));
		}
	}

	@Test
	public void checkWorkerAllowedToStartStudy()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		// Check both MTWorker and MTSandboxWorker
		MTWorker mtWorker = new MTWorker();
		addWorker(mtWorker);
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		addWorker(mtSandboxWorker);

		// It's enough to allow MTWorker to allow both MTWorker and also
		// MTSandboxWorker
		StudyModel study = importExampleStudy();
		study.addAllowedWorker(MTWorker.WORKER_TYPE);
		addStudy(study);

		studyAuthorisation.checkWorkerAllowedToStartStudy(mtWorker, study);
		studyAuthorisation.checkWorkerAllowedToStartStudy(mtSandboxWorker,
				study);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToStartStudyDidStudyAlready()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		// Check both MTWorker and MTSandboxWorker
		MTWorker mtWorker = new MTWorker();
		addWorker(mtWorker);
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		addWorker(mtSandboxWorker);

		// It's enough to allow MTWorker to allow both MTWorker and also
		// MTSandboxWorker
		StudyModel study = importExampleStudy();
		study.addAllowedWorker(MTWorker.WORKER_TYPE);
		addStudy(study);

		// MTWorker is not allowed to start an already started study regardless
		// of the StudyState
		addStudyResult(study, mtWorker, StudyState.STARTED);
		try {
			studyAuthorisation.checkWorkerAllowedToStartStudy(mtWorker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}

		// MTSandboxWorker is allowed to start again
		studyAuthorisation.checkWorkerAllowedToStartStudy(mtSandboxWorker,
				study);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		// Check both MTWorker and MTSandboxWorker
		MTWorker mtWorker = new MTWorker();
		addWorker(mtWorker);
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		addWorker(mtSandboxWorker);

		// It's enough to allow MTWorker to allow both MTWorker and also
		// MTSandboxWorker
		StudyModel study = importExampleStudy();
		study.addAllowedWorker(MTWorker.WORKER_TYPE);
		addStudy(study);

		addStudyResult(study, mtWorker, StudyState.STARTED);

		studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study);
		studyAuthorisation.checkWorkerAllowedToDoStudy(mtSandboxWorker, study);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyWrongWorkerType()
			throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		// Check both MTWorker and MTSandboxWorker
		MTWorker mtWorker = new MTWorker();
		addWorker(mtWorker);
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		addWorker(mtSandboxWorker);
		study.removeAllowedWorker(MTWorker.WORKER_TYPE);
		study.removeAllowedWorker(MTSandboxWorker.WORKER_TYPE);
		addStudy(study);

		// Study doesn't allow this worker type
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					mtErrorMessages.workerTypeNotAllowed(mtWorker
							.getUIWorkerType()));
		}

		// Study doesn't allow this worker type
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(mtSandboxWorker,
					study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					mtErrorMessages.workerTypeNotAllowed(mtSandboxWorker
							.getUIWorkerType()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyMoreThanOnce()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		MTWorker mtWorker = new MTWorker();
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		addWorker(mtWorker);
		study.addAllowedWorker(MTWorker.WORKER_TYPE);
		study.removeAllowedWorker(MTSandboxWorker.WORKER_TYPE);
		addStudy(study);

		// MTWorkers cannot repeat the same study (StudyState in FINISHED, FAIL,
		// ABORTED
		addStudyResult(study, mtWorker, StudyState.FINISHED);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		addStudyResult(study, mtWorker, StudyState.FAIL);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		addStudyResult(study, mtWorker, StudyState.ABORTED);
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(mtWorker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}

		// MTSandboxWorkers can repeat the same study
		studyAuthorisation.checkWorkerAllowedToDoStudy(mtSandboxWorker, study);

		// Clean-up
		removeStudy(study);
	}

}
