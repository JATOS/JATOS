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
import play.mvc.Http;
import services.publix.PublixErrorMessages;
import services.publix.workers.JatosStudyAuthorisation;

/**
 * @author Kristian Lange
 */
public class JatosStudyAuthorisationTest extends AbstractTest {

	private JatosStudyAuthorisation studyAuthorisation;

	@Override
	public void before() throws Exception {
		studyAuthorisation = application.injector()
				.instanceOf(JatosStudyAuthorisation.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		mockContext();
		Http.Context.current().session().put("email", admin.getEmail());

		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		addStudy(study);

		studyAuthorisation.checkWorkerAllowedToDoStudy(admin.getWorker(), study,
				batch);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyWrongWorkerType()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		// Remove Jatos worker from allowed worker types
		Batch batch = study.getDefaultBatch();
		batch.removeAllowedWorkerType(admin.getWorker().getWorkerType());

		// Study doesn't allow this worker type
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(admin.getWorker(),
					study, batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
					.workerTypeNotAllowed(admin.getWorker().getUIWorkerType(),
							study.getId(), batch.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyNotUser()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		addStudy(study);

		entityManager.getTransaction().begin();
		study.removeUser(admin);
		entityManager.getTransaction().commit();

		// User has to be a user of this study
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(admin.getWorker(),
					study, batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
					.workerNotAllowedStudy(admin.getWorker(), study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyNotLoggedIn()
			throws NoSuchAlgorithmException, IOException {
		mockContext();

		Study study = importExampleStudy();
		Batch batch = study.getDefaultBatch();
		addStudy(study);

		// User has to be logged in
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(admin.getWorker(),
					study, batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
					.workerNotAllowedStudy(admin.getWorker(), study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

}
