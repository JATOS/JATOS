package publix.services;

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
import models.common.workers.Worker;
import services.publix.PublixErrorMessages;
import services.publix.PublixHelpers;
import services.publix.PublixUtils;

/**
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

	@Test
	public void checkRetrieveWorker()
			throws NoSuchAlgorithmException, IOException, PublixException {
		// Worker ID is null
		try {
			publixUtils.retrieveWorker(null);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(PublixErrorMessages.NO_WORKERID_IN_SESSION);
		}

		// Worker ID malformed
		try {
			publixUtils.retrieveWorker("foo");
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(errorMessages.workerNotExist("foo"));
		}

		// Worker doesn't exist
		try {
			publixUtils.retrieveWorker("2");
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(errorMessages.workerNotExist("2"));
		}
	}

	@Test
	public void checkStartComponent() throws NoSuchAlgorithmException,
			IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

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

	@Test
	public void checkStartComponentFinishPriorComponentResult()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenReloadException {
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

	@Test
	public void checkStartComponentFinishReloadableComponentResult()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenReloadException {
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

	@Test
	public void checkStartComponentNotReloadable()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenReloadException {
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
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.componentNotAllowedToReload(study.getId(),
							study.getFirstComponent().getId()));
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

	@Test
	public void checkAbortStudy() throws IOException, NoSuchAlgorithmException,
			ForbiddenReloadException {
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

	@Test
	public void checkFinishStudyResultSuccessful() throws IOException,
			NoSuchAlgorithmException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);

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

		assertThat(componentResult1.getComponentState())
				.isEqualTo(ComponentState.FINISHED);
		assertThat(componentResult1.getData()).isEqualTo("test data 1");
		assertThat(componentResult2.getComponentState())
				.isEqualTo(ComponentState.FINISHED);
		assertThat(componentResult2.getData()).isEqualTo("test data 2");
		assertThat(studyResult.getStudyState())
				.isEqualTo(StudyResult.StudyState.FINISHED);
		assertThat(studyResult.getErrorMsg()).isEqualTo("error message");
		assertThat(studyResult.getEndDate()).isNotNull();
		assertThat(studyResult.getStudySessionData()).isNullOrEmpty();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkFinishAbandonedStudyResults() throws IOException,
			NoSuchAlgorithmException, BadRequestPublixException {
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

		publixUtils.finishAbandonedStudyResults(admin.getWorker(), study, null);

		assertThat(studyResult1.getStudyState())
				.isEqualTo(StudyResult.StudyState.FAIL);
		assertThat(studyResult1.getErrorMsg())
				.isEqualTo(PublixErrorMessages.ABANDONED_STUDY_BY_WORKER);
		assertThat(studyResult2.getStudyState())
				.isEqualTo(StudyResult.StudyState.FAIL);
		assertThat(studyResult2.getErrorMsg())
				.isEqualTo(PublixErrorMessages.ABANDONED_STUDY_BY_WORKER);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveWorkersLastStudyResult() throws IOException,
			NoSuchAlgorithmException, ForbiddenPublixException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		Study clone = studyService.clone(study);
		studyService.createAndPersistStudy(admin, clone);
		entityManager.getTransaction().commit();

		entityManager.getTransaction().begin();
		StudyResult studyResult1 = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		StudyResult studyResult2 = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		StudyResult studyResult3 = resultCreator.createStudyResult(clone,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult3.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		StudyResult lastStudyResult = publixUtils
				.retrieveWorkersLastStudyResult(admin.getWorker(), study);

		assertThat(lastStudyResult).isEqualTo(studyResult2);

		// Clean-up
		removeStudy(study);
		removeStudy(clone);
	}

	@Test
	public void checkRetrieveWorkersLastStudyResultNeverDidStudy()
			throws IOException, NoSuchAlgorithmException,
			ForbiddenPublixException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		Study clone = studyService.clone(study);
		studyService.createAndPersistStudy(admin, clone);
		entityManager.getTransaction().commit();

		entityManager.getTransaction().begin();
		StudyResult studyResult1 = resultCreator.createStudyResult(clone,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		StudyResult studyResult2 = resultCreator.createStudyResult(clone,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		try {
			publixUtils.retrieveWorkersLastStudyResult(admin.getWorker(),
					study);
			Fail.fail();
		} catch (ForbiddenPublixException e) {
			assertThat(e.getMessage()).isEqualTo(errorMessages
					.workerNeverDidStudy(admin.getWorker(), study.getId()));
		}

		// Clean-up
		removeStudy(study);
		removeStudy(clone);
	}

	@Test
	public void checkRetrieveWorkersLastStudyResultAlreadyFinished()
			throws IOException, NoSuchAlgorithmException,
			ForbiddenPublixException {
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
		publixUtils.finishStudyResult(true, null, studyResult2);
		entityManager.getTransaction().commit();

		try {
			publixUtils.retrieveWorkersLastStudyResult(admin.getWorker(),
					study);
			Fail.fail();
		} catch (ForbiddenPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.workerFinishedStudyAlready(admin.getWorker(),
							study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveFirstActiveComponent() throws IOException,
			NoSuchAlgorithmException, NotFoundPublixException {
		Study study = importExampleStudy();
		study.getFirstComponent().setActive(false);
		addStudy(study);

		Component component = publixUtils.retrieveFirstActiveComponent(study);
		assertThat(component).isEqualTo(study.getComponent(2));

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveFirstActiveComponentNonActive() throws IOException,
			NoSuchAlgorithmException, NotFoundPublixException {
		Study study = importExampleStudy();
		for (Component component : study.getComponentList()) {
			component.setActive(false);
		}
		addStudy(study);

		try {
			publixUtils.retrieveFirstActiveComponent(study);
			Fail.fail();
		} catch (NotFoundPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.studyHasNoActiveComponents(study.getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveNextActiveComponent()
			throws IOException, NoSuchAlgorithmException,
			NotFoundPublixException, ForbiddenReloadException {
		Study study = importExampleStudy();
		for (Component component : study.getComponentList()) {
			component.setActive(false);
		}
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		publixUtils.startComponent(study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		Component component = publixUtils
				.retrieveNextActiveComponent(studyResult);
		// Since the 2. component is not active ...
		assertThat(component).isNull();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveComponent()
			throws IOException, NotFoundPublixException,
			BadRequestPublixException, ForbiddenPublixException {
		Study study = importExampleStudy();
		study.getLastComponent().setStudy(study);
		addStudy(study);

		Component component = publixUtils.retrieveComponent(study,
				study.getLastComponent().getId());
		assertThat(component).isEqualTo(study.getLastComponent());

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveComponentWrongId()
			throws IOException, NotFoundPublixException,
			BadRequestPublixException, ForbiddenPublixException {
		Study study = importExampleStudy();
		addStudy(study);

		try {
			publixUtils.retrieveComponent(study, 999l);
			Fail.fail();
		} catch (NotFoundPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.componentNotExist(study.getId(), 999l));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveComponentWrongComponent()
			throws IOException, NotFoundPublixException,
			BadRequestPublixException, ForbiddenPublixException {
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
					errorMessages.componentNotBelongToStudy(study.getId(),
							clone.getFirstComponent().getId()));
		}

		// Clean-up
		removeStudy(study);
		removeStudy(clone);
	}

	@Test
	public void checkRetrieveComponentNotActive()
			throws IOException, NotFoundPublixException,
			BadRequestPublixException, ForbiddenPublixException {
		Study study = importExampleStudy();
		study.getFirstComponent().setActive(false);
		study.getFirstComponent().setStudy(study);
		addStudy(study);

		try {
			publixUtils.retrieveComponent(study,
					study.getFirstComponent().getId());
			Fail.fail();
		} catch (ForbiddenPublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(errorMessages.componentNotActive(study.getId(),
							study.getFirstComponent().getId()));
		}

		// Clean-up
		removeStudy(study);
	}

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

	@Test
	public void checkRetrieveComponentByPositionWrong()
			throws IOException, PublixException {
		Study study = importExampleStudy();
		addStudy(study);

		try {
			publixUtils.retrieveComponentByPosition(study.getId(), 999);
			Fail.fail();
		} catch (NotFoundPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.noComponentAtPosition(study.getId(), 999));
		}

		// Clean-up
		removeStudy(study);
	}

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

	@Test
	public void checkRetrieveStudyNotFound()
			throws NotFoundPublixException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		try {
			publixUtils.retrieveStudy(999l);
			Fail.fail();
		} catch (NotFoundPublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(errorMessages.studyNotExist(999l));
		}

		// Clean-up
		removeStudy(study);
	}

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

		try {
			publixUtils.checkComponentBelongsToStudy(study,
					clone.getFirstComponent());
			Fail.fail();
		} catch (BadRequestPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.componentNotBelongToStudy(study.getId(),
							clone.getFirstComponent().getId()));
		}

		// Clean-up
		removeStudy(study);
		removeStudy(clone);
	}

	@Test
	public void checkFinishedStudyAlready() throws IOException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		// FINISHED, ABORTED, FAIL must return true
		studyResult.setStudyState(StudyState.FINISHED);
		assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study))
				.isTrue();
		studyResult.setStudyState(StudyState.ABORTED);
		assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study))
				.isTrue();
		studyResult.setStudyState(StudyState.FAIL);
		assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study))
				.isTrue();

		// DATA_RETRIEVED, STARTED must return false
		studyResult.setStudyState(StudyState.DATA_RETRIEVED);
		assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study))
				.isFalse();
		studyResult.setStudyState(StudyState.STARTED);
		assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study))
				.isFalse();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkFinishedStudyAlreadyWrong() throws IOException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		Study clone = studyService.clone(study);
		studyService.createAndPersistStudy(admin, clone);
		entityManager.getTransaction().commit();

		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(clone,
				study.getDefaultBatch(), admin.getWorker());
		studyResult.setStudyState(StudyState.FINISHED);
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study))
				.isFalse();

		// Clean-up
		removeStudy(study);
		removeStudy(clone);
	}

	@Test
	public void checkDidStudyAlready() throws IOException {
		Study study = importExampleStudy();
		addStudy(study);

		assertThat(PublixHelpers.didStudyAlready(admin.getWorker(), study))
				.isFalse();

		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		assertThat(PublixHelpers.didStudyAlready(admin.getWorker(), study))
				.isTrue();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkStudyDone() throws IOException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		// FINISHED, ABORTED, FAIL must return true
		studyResult.setStudyState(StudyState.FINISHED);
		assertThat(PublixHelpers.studyDone(studyResult)).isTrue();
		studyResult.setStudyState(StudyState.ABORTED);
		assertThat(PublixHelpers.studyDone(studyResult)).isTrue();
		studyResult.setStudyState(StudyState.FAIL);
		assertThat(PublixHelpers.studyDone(studyResult)).isTrue();

		// DATA_RETRIEVED, STARTED must return false
		studyResult.setStudyState(StudyState.DATA_RETRIEVED);
		assertThat(PublixHelpers.studyDone(studyResult)).isFalse();
		studyResult.setStudyState(StudyState.STARTED);
		assertThat(PublixHelpers.studyDone(studyResult)).isFalse();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkComponentDone()
			throws IOException, ForbiddenReloadException {
		Study study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		ComponentResult componentResult = publixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		// Have to set study manually in test - don't know why
		componentResult.getComponent().setStudy(study);
		entityManager.getTransaction().commit();

		// A component is done if state FINISHED, ABORTED, FAIL, or RELOADED
		componentResult.setComponentState(ComponentState.FINISHED);
		assertThat(PublixHelpers.componentDone(componentResult)).isTrue();
		componentResult.setComponentState(ComponentState.ABORTED);
		assertThat(PublixHelpers.componentDone(componentResult)).isTrue();
		componentResult.setComponentState(ComponentState.FAIL);
		assertThat(PublixHelpers.componentDone(componentResult)).isTrue();
		componentResult.setComponentState(ComponentState.RELOADED);
		assertThat(PublixHelpers.componentDone(componentResult)).isTrue();

		// Not done if
		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		assertThat(PublixHelpers.componentDone(componentResult)).isFalse();
		componentResult.setComponentState(ComponentState.RESULTDATA_POSTED);
		assertThat(PublixHelpers.componentDone(componentResult)).isFalse();
		componentResult.setComponentState(ComponentState.STARTED);
		assertThat(PublixHelpers.componentDone(componentResult)).isFalse();

		// Clean-up
		removeStudy(study);
	}

}
