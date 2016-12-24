package services.publix;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.publix.BadRequestPublixException;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.ForbiddenReloadException;
import exceptions.publix.NotFoundPublixException;
import exceptions.publix.PublixException;
import general.AbstractTest;
import models.common.Component;
import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;

/**
 * Tests for class PublixUtils
 * 
 * @author Kristian Lange
 */
public abstract class PublixUtilsTest<T extends Worker> extends AbstractTest {

	protected PublixUtils<T> publixUtils;
	protected PublixErrorMessages errorMessages;

	@Override
	public void before() throws Exception {
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

	// TODO this is probably handled by the cookie service
	// @Test
	public void checkFinishAbandonedStudyResults()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult1 = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		StudyResult studyResult2 = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		// TODO changed with new ID cookies
		publixUtils.finishAbandonedStudyResults();

		// assertThat(studyResult1.getStudyState())
		// .isEqualTo(StudyResult.StudyState.FAIL);
		// assertThat(studyResult1.getErrorMsg())
		// .isEqualTo(PublixErrorMessages.ABANDONED_STUDY_BY_WORKER);
		// assertThat(studyResult2.getStudyState())
		// .isEqualTo(StudyResult.StudyState.FAIL);
		// assertThat(studyResult2.getErrorMsg())
		// .isEqualTo(PublixErrorMessages.ABANDONED_STUDY_BY_WORKER);

		// Clean-up
		removeStudy(study);
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
	public void checkComponentBelongsToStudy()
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
	public void checkComponentBelongsToStudyFail()
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

}
