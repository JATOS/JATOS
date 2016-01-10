package publix.services.workers;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;

import exceptions.publix.ForbiddenPublixException;
import general.AbstractTest;
import models.common.Batch;
import models.common.Study;
import models.common.StudyResult.StudyState;
import models.common.workers.PersonalMultipleWorker;
import services.publix.workers.PersonalMultipleStudyAuthorisation;

/**
 * @author Kristian Lange
 */
public class PersonalMultipleStudyAuthorisationTest extends AbstractTest {

	private PersonalMultipleStudyAuthorisation studyAuthorisation;

	@Override
	public void before() throws Exception {
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
		Batch batch = study.getBatchList().get(0);
		batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalMultipleWorker worker = new PersonalMultipleWorker();
		addWorker(worker);

		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyFinishedStudy()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		Study study = importExampleStudy();
		Batch batch = study.getBatchList().get(0);
		batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		addStudy(study);
		PersonalMultipleWorker worker = new PersonalMultipleWorker();
		addWorker(worker);

		// State of study has no influence. Personal multiple workers can do
		// studies multiple times
		addStudyResult(study, batch, worker, StudyState.FINISHED);
		studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch);

		addStudyResult(study, batch, worker, StudyState.FINISHED);

		// Clean-up
		removeStudy(study);
	}

}
