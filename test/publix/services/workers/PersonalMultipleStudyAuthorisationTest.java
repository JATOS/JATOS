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
import models.common.workers.PersonalMultipleWorker;
import services.publix.workers.PersonalMultipleErrorMessages;
import services.publix.workers.PersonalMultipleStudyAuthorisation;

/**
 * @author Kristian Lange
 */
public class PersonalMultipleStudyAuthorisationTest extends AbstractTest {

	private PersonalMultipleErrorMessages personalMultipleErrorMessages;
	private PersonalMultipleStudyAuthorisation studyAuthorisation;

	@Override
	public void before() throws Exception {
		personalMultipleErrorMessages = application.injector()
				.instanceOf(PersonalMultipleErrorMessages.class);
		studyAuthorisation = application.injector()
				.instanceOf(PersonalMultipleStudyAuthorisation.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalMultipleWorker worker = new PersonalMultipleWorker();
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
		addStudy(study);

		Batch batch = study.getDefaultBatch();
		batch.removeAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		PersonalMultipleWorker worker = new PersonalMultipleWorker();
		persistWorker(worker);

		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study,
					batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(personalMultipleErrorMessages
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
		batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalMultipleWorker worker = new PersonalMultipleWorker();
		persistWorker(worker);

		// State of study has no influence. Personal multiple workers can do
		// studies multiple times
		addStudyResult(study, batch, worker, StudyState.FINISHED);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

		addStudyResult(study, batch, worker, StudyState.FINISHED);

		// Clean-up
		removeStudy(study);
	}

}
