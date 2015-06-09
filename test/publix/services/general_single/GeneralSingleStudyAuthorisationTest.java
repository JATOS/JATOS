package publix.services.general_single;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.StudyModel;
import models.StudyResult.StudyState;
import models.workers.GeneralSingleWorker;

import org.fest.assertions.Fail;
import org.junit.Test;

import publix.exceptions.ForbiddenPublixException;
import publix.exceptions.PublixException;
import publix.services.PublixErrorMessages;
import publix.services.PublixUtilsTest;

import common.Global;

/**
 * @author Kristian Lange
 */
public class GeneralSingleStudyAuthorisationTest extends
		PublixUtilsTest<GeneralSingleWorker> {

	private GeneralSingleErrorMessages generalSingleErrorMessages;
	private GeneralSinglePublixUtils generalSinglePublixUtils;
	private GeneralSingleStudyAuthorisation studyAuthorisation;

	@Override
	public void before() throws Exception {
		super.before();
		generalSinglePublixUtils = Global.INJECTOR
				.getInstance(GeneralSinglePublixUtils.class);
		publixUtils = generalSinglePublixUtils;
		generalSingleErrorMessages = Global.INJECTOR
				.getInstance(GeneralSingleErrorMessages.class);
		errorMessages = generalSingleErrorMessages;
		studyAuthorisation = Global.INJECTOR
				.getInstance(GeneralSingleStudyAuthorisation.class);
	}

	@Override
	public void after() throws Exception {
		super.before();
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		study.addAllowedWorker(GeneralSingleWorker.WORKER_TYPE);
		addStudy(study);
		GeneralSingleWorker worker = new GeneralSingleWorker();
		addWorker(worker);

		addStudyResult(study, worker, StudyState.STARTED);

		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyWrongWorkerType()
			throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);
		GeneralSingleWorker worker = new GeneralSingleWorker();

		// Study doesn't allow this worker type
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					generalSingleErrorMessages.workerTypeNotAllowed(worker
							.getUIWorkerType()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyFinishedStudy()
			throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		study.addAllowedWorker(GeneralSingleWorker.WORKER_TYPE);
		addStudy(study);
		GeneralSingleWorker worker = new GeneralSingleWorker();
		addWorker(worker);

		addStudyResult(study, worker, StudyState.FINISHED);

		// General single workers can't repeat the same study
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
