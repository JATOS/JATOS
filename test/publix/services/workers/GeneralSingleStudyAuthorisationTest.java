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
import models.common.workers.GeneralSingleWorker;
import services.publix.PublixErrorMessages;
import services.publix.workers.GeneralSingleStudyAuthorisation;

/**
 * @author Kristian Lange
 */
public class GeneralSingleStudyAuthorisationTest extends AbstractTest {

	private GeneralSingleStudyAuthorisation studyAuthorisation;

	@Override
	public void before() throws Exception {
		studyAuthorisation = application.injector()
				.instanceOf(GeneralSingleStudyAuthorisation.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		batch.addAllowedWorkerType(GeneralSingleWorker.WORKER_TYPE);
		addStudy(study);
		GeneralSingleWorker worker = new GeneralSingleWorker();
		persistWorker(worker);
		addStudyResult(study, batch, worker, StudyState.STARTED);

		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyWrongWorkerType()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

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

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyFinishedStudy()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		study.getDefaultBatch()
				.addAllowedWorkerType(GeneralSingleWorker.WORKER_TYPE);
		addStudy(study);
		GeneralSingleWorker worker = new GeneralSingleWorker();
		persistWorker(worker);

		addStudyResult(study, batch, worker, StudyState.FINISHED);

		// General single workers can't repeat the same study
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
