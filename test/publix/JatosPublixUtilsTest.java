package publix;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.StudyModel;
import models.workers.GeneralSingleWorker;
import models.workers.JatosWorker;

import org.fest.assertions.Fail;
import org.junit.Test;

import play.mvc.Http;
import publix.controllers.jatos.JatosErrorMessages;
import publix.controllers.jatos.JatosPublix;
import publix.controllers.jatos.JatosPublixUtils;
import publix.controllers.jatos.JatosStudyAuthorisation;
import publix.exceptions.ForbiddenPublixException;
import publix.exceptions.PublixException;
import common.Global;
import controllers.Users;

/**
 * @author Kristian Lange
 */
public class JatosPublixUtilsTest extends PublixUtilsTest<JatosWorker> {

	private JatosErrorMessages jatosErrorMessages;
	private JatosPublixUtils jatosPublixUtils;
	private JatosStudyAuthorisation studyAuthorisation;

	@Override
	public void before() throws Exception {
		super.before();
		jatosPublixUtils = Global.INJECTOR.getInstance(JatosPublixUtils.class);
		publixUtils = jatosPublixUtils;
		jatosErrorMessages = Global.INJECTOR
				.getInstance(JatosErrorMessages.class);
		errorMessages = jatosErrorMessages;
		studyAuthorisation = Global.INJECTOR
				.getInstance(JatosStudyAuthorisation.class);
	}

	@Override
	public void after() throws Exception {
		super.before();
	}
	
	@Test
	@Override
	public void checkRetrieveWorker() throws NoSuchAlgorithmException,
			IOException, PublixException {
		super.checkRetrieveWorker();
	}

	@Test
	public void checkRetrieveTypedWorker() throws NoSuchAlgorithmException,
			IOException, PublixException {
		JatosWorker retrievedWorker = publixUtils.retrieveTypedWorker(admin
				.getWorker().getId().toString());
		assertThat(retrievedWorker.getId())
				.isEqualTo(admin.getWorker().getId());
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
					jatosErrorMessages.workerNotCorrectType(generalSingleWorker
							.getId()));
		}
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		mockContext();
		Http.Context.current().session()
				.put(Users.SESSION_EMAIL, admin.getEmail());

		StudyModel study = importExampleStudy();
		addStudy(study);

		studyAuthorisation
				.checkWorkerAllowedToDoStudy(admin.getWorker(), study);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyWrongWorkerType()
			throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		study.removeAllowedWorker(admin.getWorker().getWorkerType());
		addStudy(study);

		// Study doesn't allow this worker type
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(admin.getWorker(),
					study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					jatosErrorMessages.workerTypeNotAllowed(admin.getWorker()
							.getUIWorkerType()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyNotMember()
			throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		study.removeMember(admin);
		entityManager.getTransaction().commit();

		// User has to be a member of this study
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(admin.getWorker(),
					study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.workerNotAllowedStudy(admin.getWorker(),
							study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkWorkerAllowedToDoStudyNotLoggedIn()
			throws NoSuchAlgorithmException, IOException {
		mockContext();

		StudyModel study = importExampleStudy();
		addStudy(study);

		// User has to be logged in
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(admin.getWorker(),
					study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.workerNotAllowedStudy(admin.getWorker(),
							study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveJatosShowFromSession()
			throws ForbiddenPublixException {
		mockContext();
		Http.Context.current().session()
				.put(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);

		String jatosShow = jatosPublixUtils.retrieveJatosShowFromSession();

		assertThat(jatosShow).isEqualTo(JatosPublix.RUN_STUDY);
	}

	@Test
	public void checkRetrieveJatosShowFromSessionFail()
			throws ForbiddenPublixException {
		mockContext();

		try {
			jatosPublixUtils.retrieveJatosShowFromSession();
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(
							JatosErrorMessages.STUDY_OR_COMPONENT_NEVER_STARTED_FROM_JATOS);
		}
	}
}
