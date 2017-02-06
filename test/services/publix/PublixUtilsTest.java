package services.publix;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.publix.BadRequestPublixException;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.ForbiddenReloadException;
import exceptions.publix.InternalServerErrorPublixException;
import exceptions.publix.NotFoundPublixException;
import exceptions.publix.PublixException;
import general.AbstractTest;
import models.common.Batch;
import models.common.Component;
import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
import play.mvc.Http.Cookie;
import services.publix.idcookie.IdCookieModel;
import services.publix.idcookie.IdCookieCollection;
import services.publix.idcookie.IdCookieService;
import services.publix.idcookie.IdCookieTestHelper;

/**
 * Tests for class PublixUtils
 * 
 * @author Kristian Lange
 */
public abstract class PublixUtilsTest<T extends Worker> extends AbstractTest {

	protected PublixUtils<T> publixUtils;
	protected PublixErrorMessages errorMessages;
	private IdCookieService idCookieService;
	private IdCookieTestHelper idCookieTestHelper;

	@Override
	public void before() throws Exception {
		this.idCookieService = application.injector()
				.instanceOf(IdCookieService.class);
		this.idCookieTestHelper = application.injector()
				.instanceOf(IdCookieTestHelper.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	/**
	 * Check normal functioning of PublixUtils.retrieveWorker()
	 */
	@Test
	public void checkRetrieveWorker() throws IOException, PublixException {
		Worker worker = publixUtils.retrieveWorker(admin.getWorker().getId());
		assertThat(worker).isNotNull();
		assertThat(worker).isEqualTo(admin.getWorker());
	}

	/**
	 * Test PublixUtils.retrieveWorker(): if worker doesn't exist a
	 * ForbiddenPublixException should be thrown
	 */
	@Test
	public void checkRetrieveWorkerNotExist()
			throws IOException, PublixException {
		try {
			publixUtils.retrieveWorker(2l);
			Fail.fail();
		} catch (ForbiddenPublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.workerNotExist("2"));
		}
	}

	/**
	 * Test PublixUtils.startComponent() normal functioning
	 */
	@Test
	public void checkStartComponent() throws NoSuchAlgorithmException,
			IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);

		StudyResult studyResult = addStudyResult(study);

		entityManager.getTransaction().begin();
		ComponentResult componentResult2 = publixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		// Check that everything went normal
		assertThat(componentResult2.getComponentState())
				.isEqualTo(ComponentState.STARTED);
		assertThat(studyResult.getComponentResultList().size()).isEqualTo(1);
		assertThat(
				studyResult.getComponentResultList().get(0).getComponentState())
						.isEqualTo(ComponentState.STARTED);
		assertThat(studyResult.getComponentResultList().get(0).getStartDate())
				.isNotNull();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.startComponent(): after starting a second component in
	 * the same study run, the first component result should be finished
	 * automatically
	 */
	@Test
	public void checkStartComponentFinishPriorComponentResult()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		ComponentResult componentResult1 = publixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		// Start a different component than the prior one
		entityManager.getTransaction().begin();
		ComponentResult componentResult2 = publixUtils
				.startComponent(study.getComponent(2), studyResult);
		entityManager.getTransaction().commit();

		// Check new ComponentResult
		assertThat(componentResult2.getComponentState())
				.isEqualTo(ComponentState.STARTED);

		// Check that prior ComponentResult was finished properly
		assertThat(componentResult1.getComponentState())
				.isEqualTo(ComponentState.FINISHED);
		assertThat(studyResult.getComponentResultList().get(0).getEndDate())
				.isNotNull();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.startComponent(): after reloading the same component a
	 * new component result should be created and the old one should be finished
	 */
	@Test
	public void checkStartComponentFinishReloadableComponentResult()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		ComponentResult componentResult1 = publixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		// Start the same component a second time
		entityManager.getTransaction().begin();
		ComponentResult componentResult2 = publixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		// Check new ComponentResult
		assertThat(componentResult2.getComponentState())
				.isEqualTo(ComponentState.STARTED);

		// Check that prior ComponentResult was finished properly
		assertThat(componentResult1.getComponentState())
				.isEqualTo(ComponentState.RELOADED);
		assertThat(studyResult.getComponentResultList().get(0).getEndDate())
				.isNotNull();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.startComponent(): if one tries to reload a
	 * non-reloadable component, an ForbiddenReloadException should be thrown
	 * and the first component result should be finished
	 */
	@Test
	public void checkStartComponentNotReloadable()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		study.getFirstComponent().setReloadable(false);
		ComponentResult componentResult1 = publixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		// Start the same component a second times, but first is not reloadable
		entityManager.getTransaction().begin();
		try {
			publixUtils.startComponent(study.getFirstComponent(), studyResult);
			Fail.fail();
		} catch (ForbiddenReloadException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.componentNotAllowedToReload(
							study.getId(), study.getFirstComponent().getId()));
		}
		entityManager.getTransaction().commit();

		// Check that prior ComponentResult was finished properly
		assertThat(componentResult1.getComponentState())
				.isEqualTo(ComponentState.FAIL);
		assertThat(studyResult.getComponentResultList().get(0).getEndDate())
				.isNotNull();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.abortStudy(): check normal functioning (e.g. all study
	 * and component data should be empty also they were filled before)
	 */
	@Test
	public void checkAbortStudy() throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		studyResult.setStudySessionData("{\"test\":\"test\"}");
		ComponentResult componentResult1 = publixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		componentResult1.setData("test data 1");
		ComponentResult componentResult2 = publixUtils
				.startComponent(study.getComponent(2), studyResult);
		componentResult2.setData("test data 2");
		entityManager.getTransaction().commit();

		entityManager.getTransaction().begin();
		publixUtils.abortStudy("abort message", studyResult);
		entityManager.getTransaction().commit();

		assertThat(componentResult1.getComponentState())
				.isEqualTo(ComponentState.ABORTED);
		assertThat(componentResult1.getData()).isNullOrEmpty();
		assertThat(componentResult2.getComponentState())
				.isEqualTo(ComponentState.ABORTED);
		assertThat(componentResult2.getData()).isNullOrEmpty();
		assertThat(studyResult.getStudyState())
				.isEqualTo(StudyResult.StudyState.ABORTED);
		assertThat(studyResult.getAbortMsg()).isEqualTo("abort message");
		assertThat(studyResult.getEndDate()).isNotNull();
		assertThat(studyResult.getStudySessionData()).isNullOrEmpty();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.finishStudyResult(): normal functioning and finish
	 * successful
	 */
	@Test
	public void checkFinishStudyResultSuccessful()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);

		// Start a study and two of its components
		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		ComponentResult componentResult1 = publixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		componentResult1.setData("test data 1");
		ComponentResult componentResult2 = publixUtils
				.startComponent(study.getComponent(2), studyResult);
		componentResult2.setData("test data 2");
		entityManager.getTransaction().commit();

		entityManager.getTransaction().begin();
		publixUtils.finishStudyResult(true, "error message", studyResult);
		entityManager.getTransaction().commit();

		// Check component results
		assertThat(componentResult1.getComponentState())
				.isEqualTo(ComponentState.FINISHED);
		assertThat(componentResult1.getData()).isEqualTo("test data 1");
		assertThat(componentResult2.getComponentState())
				.isEqualTo(ComponentState.FINISHED);
		assertThat(componentResult2.getData()).isEqualTo("test data 2");

		// Check study result
		assertThat(studyResult.getStudyState())
				.isEqualTo(StudyResult.StudyState.FINISHED);
		// Not possible to check confirmation code because it depends on the
		// worker and can be null
		assertThat(studyResult.getErrorMsg()).isEqualTo("error message");
		assertThat(studyResult.getEndDate()).isNotNull();
		assertThat(studyResult.getStudySessionData()).isNullOrEmpty();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.finishStudyResult(): call the method with functioning
	 * and finish successful
	 */
	@Test
	public void checkFinishStudyResultFailed()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);

		// Start a study and two of its components
		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		ComponentResult componentResult1 = publixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		componentResult1.setData("test data 1");
		ComponentResult componentResult2 = publixUtils
				.startComponent(study.getComponent(2), studyResult);
		componentResult2.setData("test data 2");
		entityManager.getTransaction().commit();

		entityManager.getTransaction().begin();
		publixUtils.finishStudyResult(false, "error message", studyResult);
		entityManager.getTransaction().commit();

		// Check component results: first one should be finished last one
		// started (but not failed)
		assertThat(componentResult1.getComponentState())
				.isEqualTo(ComponentState.FINISHED);
		assertThat(componentResult1.getData()).isEqualTo("test data 1");
		assertThat(componentResult2.getComponentState())
				.isEqualTo(ComponentState.STARTED);
		assertThat(componentResult2.getData()).isEqualTo("test data 2");

		// Check study result
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.FAIL);
		assertThat(studyResult.getConfirmationCode()).isNull();
		assertThat(studyResult.getErrorMsg()).isEqualTo("error message");
		assertThat(studyResult.getEndDate()).isNotNull();
		assertThat(studyResult.getStudySessionData()).isNullOrEmpty();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.finishAbandonedStudyResults: if there are exactly the
	 * max allowed number of ID cookies than the oldest cookie should be deleted
	 */
	@Test
	public void checkFinishAbandonedStudyResultsEqualAllowed()
			throws IOException, PublixException {
		List<Cookie> cookieList = generateIdCookieList(
				IdCookieCollection.MAX_ID_COOKIES);
		mockContext(cookieList);

		publixUtils.finishAbandonedStudyResults();

		// Check that oldest Id cookie is gone (the one with ID 1l)
		try {
			idCookieService.getIdCookie(1l);
			Fail.fail();
		} catch (BadRequestPublixException e) {
			// just throwing the exception is enough
		}

		// Check that all other ID cookies are still there
		checkRangeOfIdCookiesExist(2, IdCookieCollection.MAX_ID_COOKIES);
	}

	/**
	 * Test PublixUtils.finishAbandonedStudyResults: if there are more ID
	 * cookies than the max allowed number than the oldest cookie should be
	 * deleted (this case should actually never happen in live - there shouldn't
	 * be more than the max allowed number of ID cookies).
	 */
	@Test
	public void checkFinishAbandonedStudyResultsMoreThanAllowed()
			throws IOException, PublixException {
		List<Cookie> cookieList = generateIdCookieList(
				IdCookieCollection.MAX_ID_COOKIES + 1);
		mockContext(cookieList);

		publixUtils.finishAbandonedStudyResults();

		// Check that oldest Id cookie is gone (the one with ID 1l)
		try {
			idCookieService.getIdCookie(1l);
			Fail.fail();
		} catch (BadRequestPublixException e) {
			// just throwing the exception is enough
		}

		// Check that all other ID cookies are still there
		checkRangeOfIdCookiesExist(2, IdCookieCollection.MAX_ID_COOKIES + 1);
	}

	/**
	 * Test PublixUtils.finishAbandonedStudyResults: if there are less ID
	 * cookies than the max allowed number than all ID cookies should be kept
	 */
	@Test
	public void checkFinishAbandonedStudyResultsNoDeleting()
			throws IOException, PublixException {
		List<Cookie> cookieList = generateIdCookieList(
				IdCookieCollection.MAX_ID_COOKIES - 1);
		mockContext(cookieList);

		publixUtils.finishAbandonedStudyResults();

		// Check that all ID cookies are still there
		checkRangeOfIdCookiesExist(1, IdCookieCollection.MAX_ID_COOKIES - 1);
	}

	/**
	 * Test PublixUtils.finishAbandonedStudyResults: function should work even
	 * if there are no ID cookies yet
	 */
	@Test
	public void checkFinishAbandonedStudyResultsEmpty()
			throws IOException, PublixException {
		// Generate empty ID cookie list
		List<Cookie> cookieList = new ArrayList<>();
		mockContext(cookieList);

		publixUtils.finishAbandonedStudyResults();

		// Check that there is still no ID cookie
		assertThat(idCookieService.getOldestIdCookie()).isNull();
	}

	private void checkRangeOfIdCookiesExist(int from, int to)
			throws BadRequestPublixException,
			InternalServerErrorPublixException {
		for (long i = from; i <= to; i++) {
			idCookieService.getIdCookie(i);
		}
	}

	private List<Cookie> generateIdCookieList(int size) {
		List<Cookie> cookieList = new ArrayList<>();
		for (long i = 1l; i <= size; i++) {
			IdCookieModel idCookie = idCookieTestHelper.buildDummyIdCookie(i);
			cookieList.add(idCookieTestHelper.buildCookie(idCookie));
		}
		return cookieList;
	}

	/**
	 * Checks the normal functioning of PublixUtils.retrieveStudyResult():
	 * should return the correct study result
	 */
	@Test
	public void checkRetrieveStudyResult() throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		StudyResult studyResult1 = addStudyResult(study);
		StudyResult studyResult2 = addStudyResult(study);

		StudyResult persistedStudyResult1 = publixUtils.retrieveStudyResult(
				admin.getWorker(), study, studyResult1.getId());
		assertThat(persistedStudyResult1).isEqualTo(studyResult1);
		StudyResult persistedStudyResult2 = publixUtils.retrieveStudyResult(
				admin.getWorker(), study, studyResult2.getId());
		assertThat(persistedStudyResult2).isEqualTo(studyResult2);

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveStudyResult(): It should throw an
	 * ForbiddenPublixException if the requested study result doesn't belong to
	 * the given study
	 */
	@Test
	public void checkRetrieveStudyResultNotFromThisStudy()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		StudyResult studyResult1 = addStudyResult(study);

		// We need a second study
		entityManager.getTransaction().begin();
		Study clone = studyService.clone(study);
		studyService.createAndPersistStudy(admin, clone);
		entityManager.getTransaction().commit();

		try {
			publixUtils.retrieveStudyResult(admin.getWorker(), clone,
					studyResult1.getId());
			Fail.fail();
		} catch (ForbiddenPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.STUDY_RESULT_DOESN_T_BELONG_TO_THIS_STUDY);
		}

		// Clean-up
		removeStudy(study);
		removeStudy(clone);
	}

	/**
	 * Tests PublixUtils.retrieveStudyResult(): Any study result is associated
	 * with a worker. If the wrong worker wants to retrieve the result a
	 * ForbiddenPublixException must be thrown.
	 */
	@Test
	public void checkRetrieveStudyResultNotFromThisWorker()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		StudyResult studyResult1 = addStudyResult(study);

		// Create another worker (type is not important here)
		JatosWorker worker = new JatosWorker();
		persistWorker(worker);

		try {
			publixUtils.retrieveStudyResult(worker, study,
					studyResult1.getId());
			Fail.fail();
		} catch (ForbiddenPublixException e) {
			assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
					.workerNeverDidStudy(worker, study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveStudyResult(): should throw a
	 * BadRequestPublixException if the study result isn't present in the DB
	 */
	@Test
	public void checkRetrieveStudyResultNeverDidStudy()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		// Never started any study
		try {
			publixUtils.retrieveStudyResult(admin.getWorker(), study, 1l);
			Fail.fail();
		} catch (BadRequestPublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.STUDY_RESULT_DOESN_T_EXIST);
		}

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveStudyResult(): should throw a
	 * ForbiddenPublixException if the study result is already done
	 */
	@Test
	public void checkRetrieveStudyResultAlreadyFinished()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		StudyResult studyResult1 = addStudyResult(study);

		entityManager.getTransaction().begin();
		publixUtils.finishStudyResult(true, null, studyResult1);
		entityManager.getTransaction().commit();

		try {
			publixUtils.retrieveStudyResult(admin.getWorker(), study,
					studyResult1.getId());
			Fail.fail();
		} catch (ForbiddenPublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.workerFinishedStudyAlready(
							admin.getWorker(), study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveLastComponentResult(): check that the last
	 * component result is returned
	 */
	@Test
	public void checkRetrieveLastComponentResult()
			throws IOException, PublixException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		StudyResult studyResult = addStudyResult(study);

		// Create two component results
		entityManager.getTransaction().begin();
		publixUtils.startComponent(study.getFirstComponent(), studyResult);
		ComponentResult componentResult2 = publixUtils
				.startComponent(study.getComponent(2), studyResult);
		entityManager.getTransaction().commit();

		// Check that the second result is returned
		ComponentResult retrievedComponentResult = publixUtils
				.retrieveLastComponentResult(studyResult);
		assertThat(retrievedComponentResult).isEqualTo(componentResult2);

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveLastComponentResult(): if no component result
	 * exist null should be returned
	 */
	@Test
	public void checkRetrieveLastComponentResultEmpty()
			throws IOException, PublixException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		StudyResult studyResult = addStudyResult(study);

		// Check that null is returned
		ComponentResult retrievedComponentResult = publixUtils
				.retrieveLastComponentResult(studyResult);
		assertThat(retrievedComponentResult).isNull();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveLastComponent(): check that the last component
	 * is returned
	 */
	@Test
	public void checkRetrieveLastComponent()
			throws IOException, PublixException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		StudyResult studyResult = addStudyResult(study);

		// Create two component results
		entityManager.getTransaction().begin();
		publixUtils.startComponent(study.getFirstComponent(), studyResult);
		publixUtils.startComponent(study.getComponent(2), studyResult);
		entityManager.getTransaction().commit();

		// Check that the second result is returned
		Component retrievedComponent = publixUtils
				.retrieveLastComponent(studyResult);
		assertThat(retrievedComponent).isEqualTo(study.getComponent(2));

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveLastComponent(): if no component exist null
	 * should be returned
	 */
	@Test
	public void checkRetrieveLastComponentEmpty()
			throws IOException, PublixException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		StudyResult studyResult = addStudyResult(study);

		// Check that null is returned
		Component retrievedComponent = publixUtils
				.retrieveLastComponent(studyResult);
		assertThat(retrievedComponent).isNull();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveCurrentComponentResult(): check that the last
	 * component result is returned if it is not 'done'
	 */
	@Test
	public void checkRetrieveCurrentComponentResult()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		StudyResult studyResult = addStudyResult(study);

		// Create two component results
		entityManager.getTransaction().begin();
		publixUtils.startComponent(study.getFirstComponent(), studyResult);
		ComponentResult componentResult2 = publixUtils
				.startComponent(study.getComponent(2), studyResult);
		entityManager.getTransaction().commit();

		// Check that the second result is returned
		ComponentResult retrievedComponentResult = publixUtils
				.retrieveCurrentComponentResult(studyResult);
		assertThat(retrievedComponentResult).isEqualTo(componentResult2);

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveCurrentComponentResult(): check that null is
	 * returned if the last component result is 'done'
	 */
	@Test
	public void checkRetrieveCurrentComponentResultIfDone()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		StudyResult studyResult = addStudyResult(study);

		// Create two component results
		entityManager.getTransaction().begin();
		publixUtils.startComponent(study.getFirstComponent(), studyResult);
		ComponentResult componentResult2 = publixUtils
				.startComponent(study.getComponent(2), studyResult);
		componentResult2.setComponentState(ComponentState.FINISHED);
		entityManager.getTransaction().commit();

		// Check that null is returned
		ComponentResult retrievedComponentResult = publixUtils
				.retrieveCurrentComponentResult(studyResult);
		assertThat(retrievedComponentResult).isNull();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveStartedComponentResult(): check that the last
	 * component result is returned if it is not 'done'
	 */
	@Test
	public void checkRetrieveStartedComponentResult()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		StudyResult studyResult = addStudyResult(study);

		// Create two component results
		entityManager.getTransaction().begin();
		publixUtils.startComponent(study.getFirstComponent(), studyResult);
		ComponentResult componentResult2 = publixUtils
				.startComponent(study.getComponent(2), studyResult);
		entityManager.getTransaction().commit();

		// Check that the second result is returned since it is not 'done'
		ComponentResult retrievedComponentResult = publixUtils
				.retrieveStartedComponentResult(study.getComponent(3),
						studyResult);
		assertThat(retrievedComponentResult).isEqualTo(componentResult2);

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveStartedComponentResult(): check that a new
	 * component result is returned if the last one is 'done'
	 */
	@Test
	public void checkRetrieveStartedComponentResultDone()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);
		StudyResult studyResult = addStudyResult(study);

		// Create two component results
		entityManager.getTransaction().begin();
		publixUtils.startComponent(study.getFirstComponent(), studyResult);
		ComponentResult componentResult2 = publixUtils
				.startComponent(study.getComponent(2), studyResult);
		componentResult2.setComponentState(ComponentState.FINISHED);
		entityManager.getTransaction().commit();

		// Check that a new component result for the 2rd component is returned
		// since the last one is 'done'
		entityManager.getTransaction().begin();
		ComponentResult retrievedComponentResult = publixUtils
				.retrieveStartedComponentResult(study.getComponent(2),
						studyResult);
		entityManager.getTransaction().commit();
		assertThat(retrievedComponentResult).isNotEqualTo(componentResult2);
		assertThat(retrievedComponentResult.getComponent())
				.isEqualTo(study.getComponent(2));

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveFirstActiveComponent(): normal functioning
	 */
	@Test
	public void checkRetrieveFirstActiveComponent()
			throws IOException, NotFoundPublixException {
		Study study = importExampleStudy();
		study.getFirstComponent().setActive(false);
		addStudy(study);

		Component component = publixUtils.retrieveFirstActiveComponent(study);
		assertThat(component).isEqualTo(study.getComponent(2));

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Tests PublixUtils.retrieveFirstActiveComponent(): if there is no active
	 * component an NotFoundPublixException should be thrown
	 */
	@Test
	public void checkRetrieveFirstActiveComponentNotFound() throws IOException {
		Study study = importExampleStudy();
		for (Component component : study.getComponentList()) {
			component.setActive(false);
		}
		addStudy(study);

		try {
			publixUtils.retrieveFirstActiveComponent(study);
			Fail.fail();
		} catch (NotFoundPublixException e) {
			assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
					.studyHasNoActiveComponents(study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.retrieveNextActiveComponent(): normal functioning
	 */
	@Test
	public void checkRetrieveNextActiveComponent()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);

		// Start a study and the first component
		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		publixUtils.startComponent(study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		Component component = publixUtils
				.retrieveNextActiveComponent(studyResult);
		// Next component is the 2nd
		assertThat(component).isEqualTo(study.getComponent(2));

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.retrieveNextActiveComponent(): no next active component
	 * can be found
	 */
	@Test
	public void checkRetrieveNextActiveComponentNotFound()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		// Inactivate all components
		for (Component component : study.getComponentList()) {
			component.setActive(false);
		}
		addStudy(study);

		// Start a study and the first component
		StudyResult studyResult = addStudyResult(study);
		entityManager.getTransaction().begin();
		publixUtils.startComponent(study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		Component component = publixUtils
				.retrieveNextActiveComponent(studyResult);
		// Since all components are not active it should be null
		assertThat(component).isNull();

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.retrieveComponent(): normal functioning
	 */
	@Test
	public void checkRetrieveComponent()
			throws IOException, NotFoundPublixException,
			BadRequestPublixException, ForbiddenPublixException {
		Study study = importExampleStudy();
		study.getLastComponent().setStudy(study);
		addStudy(study);

		// Retrieve last component
		Component component = publixUtils.retrieveComponent(study,
				study.getLastComponent().getId());
		assertThat(component).isEqualTo(study.getLastComponent());

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.retrieveComponent(): If an component with this ID
	 * doesn't exist for this study an NotFoundPublixException should be thrown
	 */
	@Test
	public void checkRetrieveComponentWrongId() throws IOException,
			BadRequestPublixException, ForbiddenPublixException {
		Study study = importExampleStudy();
		addStudy(study);

		try {
			publixUtils.retrieveComponent(study, 999l);
			Fail.fail();
		} catch (NotFoundPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.componentNotExist(study.getId(), 999l));
		}

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.retrieveComponent(): If the component doesn't belong to
	 * this study an BadRequestPublixException should be thrown
	 */
	@Test
	public void checkRetrieveComponentNotOfThisStudy() throws IOException,
			NotFoundPublixException, ForbiddenPublixException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		Study clone = studyService.clone(study);
		studyService.createAndPersistStudy(admin, clone);
		entityManager.getTransaction().commit();

		try {
			publixUtils.retrieveComponent(study,
					clone.getFirstComponent().getId());
			Fail.fail();
		} catch (BadRequestPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.componentNotBelongToStudy(study.getId(),
							clone.getFirstComponent().getId()));
		}

		// Clean-up
		removeStudy(study);
		removeStudy(clone);
	}

	/**
	 * Test PublixUtils.retrieveComponent(): If the component isn't active an
	 * ForbiddenPublixException should be thrown
	 */
	@Test
	public void checkRetrieveComponentNotActive() throws IOException,
			NotFoundPublixException, BadRequestPublixException {
		Study study = importExampleStudy();
		study.getFirstComponent().setActive(false);
		study.getFirstComponent().setStudy(study);
		addStudy(study);

		try {
			publixUtils.retrieveComponent(study,
					study.getFirstComponent().getId());
			Fail.fail();
		} catch (ForbiddenPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.componentNotActive(study.getId(),
							study.getFirstComponent().getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test retrieveComponentByPosition(): normal functioning
	 */
	@Test
	public void checkRetrieveComponentByPosition()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		Component component = publixUtils
				.retrieveComponentByPosition(study.getId(), 1);
		assertThat(component).isEqualTo(study.getFirstComponent());

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test retrieveComponentByPosition(): if the position parameter must is
	 * null a BadRequestPublixException must be thrown
	 */
	@Test
	public void checkRetrieveComponentByPositionNull()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		try {
			publixUtils.retrieveComponentByPosition(study.getId(), null);
			Fail.fail();
		} catch (BadRequestPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.COMPONENTS_POSITION_NOT_NULL);
		}

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test retrieveComponentByPosition(): if there is no component at this
	 * position an NotFoundPublixException should be thrown
	 */
	@Test
	public void checkRetrieveComponentByPositionWrong()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		try {
			publixUtils.retrieveComponentByPosition(study.getId(), 999);
			Fail.fail();
		} catch (NotFoundPublixException e) {
			assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
					.noComponentAtPosition(study.getId(), 999));
		}

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.retrieveStudy(): normal functioning
	 */
	@Test
	public void checkRetrieveStudy()
			throws NotFoundPublixException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		Study retrievedStudy = publixUtils.retrieveStudy(study.getId());
		assertThat(retrievedStudy).isEqualTo(study);

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Test PublixUtils.retrieveStudy(): if a study with this ID doesn't exist
	 * in DB a NotFoundPublixException should be thrown
	 */
	@Test
	public void checkRetrieveStudyNotFound() throws IOException {
		Study study = importExampleStudy();
		addStudy(study);

		try {
			publixUtils.retrieveStudy(999l);
			Fail.fail();
		} catch (NotFoundPublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.studyNotExist(999l));
		}

		// Clean-up
		removeStudy(study);
	}

	/**
	 * PublixUtils.checkComponentBelongsToStudy(): normal functioning - if the
	 * component belongs to the study the method should just return
	 */
	@Test
	public void checkCheckComponentBelongsToStudy()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		study.getFirstComponent().setStudy(study);
		addStudy(study);

		publixUtils.checkComponentBelongsToStudy(study,
				study.getFirstComponent());

		// Clean-up
		removeStudy(study);
	}

	/**
	 * PublixUtils.checkComponentBelongsToStudy(): if the component does not
	 * belong to the study the method should throw a BadRequestPublixException
	 */
	@Test
	public void checkCheckComponentBelongsToStudyFail()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		study.getFirstComponent().setStudy(study);
		addStudy(study);

		entityManager.getTransaction().begin();
		Study clone = studyService.clone(study);
		studyService.createAndPersistStudy(admin, clone);
		entityManager.getTransaction().commit();

		// Check if component of 'clone' belongs to 'study'
		try {
			publixUtils.checkComponentBelongsToStudy(study,
					clone.getFirstComponent());
			Fail.fail();
		} catch (BadRequestPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.componentNotBelongToStudy(study.getId(),
							clone.getFirstComponent().getId()));
		}

		// Clean-up
		removeStudy(study);
		removeStudy(clone);
	}

	/**
	 * PublixUtils.checkStudyIsGroupStudy()
	 */
	@Test
	public void checkCheckStudyIsGroupStudy()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		study.setGroupStudy(true);
		addStudy(study);

		// Since it's a group study the method should just return
		publixUtils.checkStudyIsGroupStudy(study);

		// Clean-up
		removeStudy(study);
	}

	/**
	 * PublixUtils.checkStudyIsGroupStudy()
	 */
	@Test
	public void checkCheckStudyIsGroupStudyFalse()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		study.setGroupStudy(false);
		addStudy(study);

		// Since it's not a group study the method should just throw an
		// exception
		try {
			publixUtils.checkStudyIsGroupStudy(study);
			Fail.fail();
		} catch (ForbiddenPublixException e) {
			// Just an exception is fine
		}

		// Clean-up
		removeStudy(study);
	}

	/**
	 * PublixUtils.retrieveBatchByIdOrDefault(): get default batch if batch ID
	 * is -1
	 */
	@Test
	public void checkRetrieveBatchByIdOrDefaultDefault()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		Batch retrievedBatch = publixUtils.retrieveBatchByIdOrDefault(-1l,
				study);
		assertThat(retrievedBatch).isEqualTo(study.getDefaultBatch());

		// Clean-up
		removeStudy(study);
	}

	/**
	 * PublixUtils.retrieveBatchByIdOrDefault(): get batch specified by ID
	 */
	@Test
	public void checkRetrieveBatchByIdOrDefaultById()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		Batch batch2 = batchService.clone(study.getDefaultBatch());
		batch2.setTitle("Test Title");
		batchService.createAndPersistBatch(batch2, study, admin);
		entityManager.getTransaction().commit();

		Batch retrievedBatch = publixUtils
				.retrieveBatchByIdOrDefault(batch2.getId(), study);
		assertThat(retrievedBatch).isEqualTo(batch2);

		// Clean-up
		removeStudy(study);
	}

	/**
	 * PublixUtils.retrieveBatch(): get batch specified by ID
	 */
	@Test
	public void checkRetrieveBatch() throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		Batch batch2 = batchService.clone(study.getDefaultBatch());
		batch2.setTitle("Test Title");
		batchService.createAndPersistBatch(batch2, study, admin);
		entityManager.getTransaction().commit();

		Batch retrievedBatch = publixUtils.retrieveBatch(batch2.getId());
		assertThat(retrievedBatch).isEqualTo(batch2);

		// Clean-up
		removeStudy(study);
	}

	/**
	 * PublixUtils.retrieveBatch(): if a batch with the specified ID doesn't
	 * exist throw an ForbiddenPublixException
	 */
	@Test
	public void checkRetrieveBatchFail() throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		try {
			publixUtils.retrieveBatch(999l);
			Fail.fail();
		} catch (ForbiddenPublixException e) {
			// Just an exception is fine
		}

		// Clean-up
		removeStudy(study);
	}

	/**
	 * PublixUtils.setPreStudyStateByPre()
	 */
	@Test
	public void checkSetPreStudyStateByPre()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		StudyResult studyResult = addStudyResult(study);

		publixUtils.setPreStudyStateByPre(true, studyResult);
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.PRE);
		publixUtils.setPreStudyStateByPre(false, studyResult);
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.STARTED);

		// Clean-up
		removeStudy(study);
	}

	/**
	 * PublixUtils.setPreStudyStateByComponentId(): should set study result's to
	 * STARTED only and only if the state is originally in PRE and it is not the
	 * first component
	 */
	@Test
	public void checkSetPreStudyStateByComponentId()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		StudyResult studyResult = addStudyResult(study);

		// PRE && first => stays in PRE
		studyResult.setStudyState(StudyState.PRE);
		publixUtils.setPreStudyStateByComponentId(studyResult, study,
				study.getFirstComponent().getId());
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.PRE);

		// STARTED && first => keeps state
		studyResult.setStudyState(StudyState.STARTED);
		publixUtils.setPreStudyStateByComponentId(studyResult, study,
				study.getFirstComponent().getId());
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.STARTED);

		// PRE && second => changes to STARTED
		studyResult.setStudyState(StudyState.PRE);
		publixUtils.setPreStudyStateByComponentId(studyResult, study,
				study.getComponent(2).getId());
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.STARTED);

		// STARTED && second => keeps state
		studyResult.setStudyState(StudyState.STARTED);
		publixUtils.setPreStudyStateByComponentId(studyResult, study,
				study.getComponent(2).getId());
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.STARTED);

		// Clean-up
		removeStudy(study);
	}

}
