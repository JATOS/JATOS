package publix;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.StudyModel;
import models.StudyResult;
import models.StudyResult.StudyState;
import models.workers.GeneralSingleWorker;

import org.fest.assertions.Fail;
import org.junit.Test;

import play.mvc.Http.Cookie;
import common.Global;
import controllers.publix.PublixErrorMessages;
import controllers.publix.general_single.GeneralSingleErrorMessages;
import controllers.publix.general_single.GeneralSinglePublixUtils;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;

/**
 * @author Kristian Lange
 */
public class GeneralSinglePublixUtilsTest extends
		PublixUtilsTest<GeneralSingleWorker> {

	private GeneralSingleErrorMessages generalSingleErrorMessages;
	private GeneralSinglePublixUtils generalSinglePublixUtils;

	@Override
	public void before() throws Exception {
		super.before();
		generalSinglePublixUtils = Global.INJECTOR
				.getInstance(GeneralSinglePublixUtils.class);
		publixUtils = generalSinglePublixUtils;
		generalSingleErrorMessages = Global.INJECTOR
				.getInstance(GeneralSingleErrorMessages.class);
		errorMessages = generalSingleErrorMessages;
	}

	@Override
	public void after() throws Exception {
		super.before();
	}

	@Test
	public void checkRetrieveTypedWorker() throws NoSuchAlgorithmException,
			IOException, PublixException {
		GeneralSingleWorker worker = new GeneralSingleWorker();
		entityManager.getTransaction().begin();
		workerDao.create(worker);
		entityManager.getTransaction().commit();

		publixUtils.retrieveTypedWorker(worker.getId().toString());
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
					generalSingleErrorMessages.workerNotCorrectType(admin
							.getWorker().getId()));
		}
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		study.addAllowedWorker(GeneralSingleWorker.WORKER_TYPE);
		addStudy(study);
		GeneralSingleWorker worker = new GeneralSingleWorker();
		addWorker(worker);

		// Create StudyResult for this study and worker
		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study, worker);
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(worker);
		entityManager.getTransaction().commit();

		publixUtils.checkWorkerAllowedToDoStudy(worker, study);

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
			publixUtils.checkWorkerAllowedToDoStudy(worker, study);
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

		// Create StudyResult with state FINISHED for this study and worker
		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study, worker);
		studyResult.setStudyState(StudyState.FINISHED);
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(worker);
		entityManager.getTransaction().commit();

		// General single workers can't repeat the same study
		try {
			publixUtils.checkWorkerAllowedToDoStudy(worker, study);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		Cookie cookie = mock(Cookie.class);
		// Done studies but not this one
		when(cookie.value()).thenReturn("3,4,5");
		generalSinglePublixUtils.checkStudyInCookie(study, cookie);

		// Null cookie is allowed
		generalSinglePublixUtils.checkStudyInCookie(study, null);

		// Empty cookie value is allowed
		when(cookie.value()).thenReturn("");
		generalSinglePublixUtils.checkStudyInCookie(study, cookie);

		// Weired cookie value is allowed
		when(cookie.value()).thenReturn("foo");
		generalSinglePublixUtils.checkStudyInCookie(study, cookie);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkAllowedToDoStudyAlreadyDone()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		Cookie cookie = mock(Cookie.class);
		// Put this study ID into the cookie
		when(cookie.value()).thenReturn(study.getUuid());

		try {
			generalSinglePublixUtils.checkStudyInCookie(study, cookie);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void addStudyToCookie() throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		Cookie cookie = mock(Cookie.class);

		// Cookie with two study IDs
		when(cookie.value()).thenReturn("10,20");
		String cookieValue = generalSinglePublixUtils.addStudyToCookie(study,
				cookie);
		assertThat(cookieValue).endsWith("," + study.getUuid());

		// No cookie
		cookieValue = generalSinglePublixUtils.addStudyToCookie(study, null);
		assertThat(cookieValue).isEqualTo(study.getUuid());

		// Clean-up
		removeStudy(study);
	}

}
