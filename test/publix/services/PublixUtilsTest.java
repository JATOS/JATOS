package publix.services;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import models.ComponentModel;
import models.ComponentResult;
import models.ComponentResult.ComponentState;
import models.StudyModel;
import models.StudyResult;
import models.StudyResult.StudyState;
import models.workers.Worker;

import org.fest.assertions.Fail;
import org.junit.Test;

import persistance.StudyResultDao;
import persistance.workers.WorkerDao;
import publix.controllers.Publix;
import publix.exceptions.BadRequestPublixException;
import publix.exceptions.ForbiddenPublixException;
import publix.exceptions.ForbiddenReloadException;
import publix.exceptions.NotFoundPublixException;
import publix.exceptions.PublixException;

import common.AbstractTest;
import common.Global;

/**
 * @author Kristian Lange
 */
public abstract class PublixUtilsTest<T extends Worker> extends AbstractTest {

	protected PublixUtils<T> publixUtils;
	protected PublixErrorMessages errorMessages;

	@Override
	public void before() throws Exception {
		workerDao = Global.INJECTOR.getInstance(WorkerDao.class);
		studyResultDao = Global.INJECTOR.getInstance(StudyResultDao.class);
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
	public void checkRetrieveWorker() throws NoSuchAlgorithmException,
			IOException, PublixException {
		// Worker ID is null
		try {
			publixUtils.retrieveWorker(null);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					PublixErrorMessages.NO_WORKERID_IN_SESSION);
		}

		// Worker ID malformed
		try {
			publixUtils.retrieveWorker("foo");
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.workerNotExist("foo"));
		}

		// Worker doesn't exist
		try {
			publixUtils.retrieveWorker("2");
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.workerNotExist("2"));
		}
	}

	@Test
	public void checkStartComponent() throws NoSuchAlgorithmException,
			IOException, ForbiddenReloadException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		entityManager.getTransaction().begin();
		ComponentResult componentResult2 = publixUtils.startComponent(
				study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		// Check that everything went normal
		assertThat(componentResult2.getComponentState()).isEqualTo(
				ComponentState.STARTED);
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
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		ComponentResult componentResult1 = publixUtils.startComponent(
				study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		// Start a different component than the prior one
		entityManager.getTransaction().begin();
		ComponentResult componentResult2 = publixUtils.startComponent(
				study.getComponent(2), studyResult);
		entityManager.getTransaction().commit();

		// Check new ComponentResult
		assertThat(componentResult2.getComponentState()).isEqualTo(
				ComponentState.STARTED);

		// Check that prior ComponentResult was finished properly
		assertThat(componentResult1.getComponentState()).isEqualTo(
				ComponentState.FINISHED);
		assertThat(studyResult.getComponentResultList().get(0).getEndDate())
				.isNotNull();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkStartComponentFinishReloadableComponentResult()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenReloadException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		ComponentResult componentResult1 = publixUtils.startComponent(
				study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		// Start the same component a second time
		entityManager.getTransaction().begin();
		ComponentResult componentResult2 = publixUtils.startComponent(
				study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		// Check new ComponentResult
		assertThat(componentResult2.getComponentState()).isEqualTo(
				ComponentState.STARTED);

		// Check that prior ComponentResult was finished properly
		assertThat(componentResult1.getComponentState()).isEqualTo(
				ComponentState.RELOADED);
		assertThat(studyResult.getComponentResultList().get(0).getEndDate())
				.isNotNull();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkStartComponentNotReloadable()
			throws NoSuchAlgorithmException, IOException,
			ForbiddenReloadException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		study.getFirstComponent().setReloadable(false);
		ComponentResult componentResult1 = publixUtils.startComponent(
				study.getFirstComponent(), studyResult);
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
		assertThat(componentResult1.getComponentState()).isEqualTo(
				ComponentState.FAIL);
		assertThat(studyResult.getComponentResultList().get(0).getEndDate())
				.isNotNull();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkGenerateIdCookieValue() throws NoSuchAlgorithmException,
			IOException, ForbiddenReloadException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		ComponentResult componentResult = publixUtils.startComponent(
				study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		String cookieValue = publixUtils.generateIdCookieValue(studyResult,
				componentResult, admin.getWorker(),
				studyResult.getGroup());

		// Check IDs in cookie value String
		Map<String, String> cookieMap = new HashMap<>();
		String[] idMappings = cookieValue.split("&");
		for (String idMappingStr : idMappings) {
			String[] idMapping = idMappingStr.split("=");
			cookieMap.put(idMapping[0], idMapping[1]);
		}
		assertThat(cookieMap.get(Publix.WORKER_ID)).isEqualTo("1");
		assertThat(cookieMap.get(Publix.STUDY_ID)).isEqualTo("1");
		assertThat(cookieMap.get(Publix.STUDY_RESULT_ID)).isEqualTo("1");
		assertThat(cookieMap.get(Publix.COMPONENT_ID)).isEqualTo("1");
		assertThat(cookieMap.get(Publix.COMPONENT_RESULT_ID)).isEqualTo("1");
		assertThat(cookieMap.get(Publix.COMPONENT_POSITION)).isEqualTo("1");

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkAbortStudy() throws IOException, NoSuchAlgorithmException,
			ForbiddenReloadException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		studyResult.setStudySessionData("{\"test\":\"test\"}");
		ComponentResult componentResult1 = publixUtils.startComponent(
				study.getFirstComponent(), studyResult);
		componentResult1.setData("test data 1");
		ComponentResult componentResult2 = publixUtils.startComponent(
				study.getComponent(2), studyResult);
		componentResult2.setData("test data 2");
		entityManager.getTransaction().commit();

		entityManager.getTransaction().begin();
		publixUtils.abortStudy("abort message", studyResult);
		entityManager.getTransaction().commit();

		assertThat(componentResult1.getComponentState()).isEqualTo(
				ComponentState.FINISHED);
		assertThat(componentResult1.getData()).isNullOrEmpty();
		assertThat(componentResult2.getComponentState()).isEqualTo(
				ComponentState.ABORTED);
		assertThat(componentResult2.getData()).isNullOrEmpty();
		assertThat(studyResult.getStudyState()).isEqualTo(
				StudyResult.StudyState.ABORTED);
		assertThat(studyResult.getAbortMsg()).isEqualTo("abort message");
		assertThat(studyResult.getEndDate()).isNotNull();
		assertThat(studyResult.getStudySessionData()).isNullOrEmpty();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkFinishStudyResultSuccessful() throws IOException,
			NoSuchAlgorithmException, ForbiddenReloadException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		ComponentResult componentResult1 = publixUtils.startComponent(
				study.getFirstComponent(), studyResult);
		componentResult1.setData("test data 1");
		ComponentResult componentResult2 = publixUtils.startComponent(
				study.getComponent(2), studyResult);
		componentResult2.setData("test data 2");
		entityManager.getTransaction().commit();

		entityManager.getTransaction().begin();
		publixUtils.finishStudyResult(true, "error message", studyResult);
		entityManager.getTransaction().commit();

		assertThat(componentResult1.getComponentState()).isEqualTo(
				ComponentState.FINISHED);
		assertThat(componentResult1.getData()).isEqualTo("test data 1");
		assertThat(componentResult2.getComponentState()).isEqualTo(
				ComponentState.FINISHED);
		assertThat(componentResult2.getData()).isEqualTo("test data 2");
		assertThat(studyResult.getStudyState()).isEqualTo(
				StudyResult.StudyState.FINISHED);
		assertThat(studyResult.getErrorMsg()).isEqualTo("error message");
		assertThat(studyResult.getEndDate()).isNotNull();
		assertThat(studyResult.getStudySessionData()).isNullOrEmpty();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkFinishAllPriorStudyResults() throws IOException,
			NoSuchAlgorithmException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult1 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		StudyResult studyResult2 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		publixUtils.finishAllPriorStudyResults(admin.getWorker(), study);

		assertThat(studyResult1.getStudyState()).isEqualTo(
				StudyResult.StudyState.FAIL);
		assertThat(studyResult1.getErrorMsg()).isEqualTo(
				PublixErrorMessages.STUDY_NEVER_FINSHED);
		assertThat(studyResult2.getStudyState()).isEqualTo(
				StudyResult.StudyState.FAIL);
		assertThat(studyResult2.getErrorMsg()).isEqualTo(
				PublixErrorMessages.STUDY_NEVER_FINSHED);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveWorkersLastStudyResult() throws IOException,
			NoSuchAlgorithmException, ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyModel clone = studyService.cloneStudy(study, admin);
		entityManager.getTransaction().commit();

		entityManager.getTransaction().begin();
		StudyResult studyResult1 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		StudyResult studyResult2 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		StudyResult studyResult3 = studyResultDao.create(clone,
				admin.getWorker());
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
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyModel clone = studyService.cloneStudy(study, admin);
		entityManager.getTransaction().commit();

		entityManager.getTransaction().begin();
		StudyResult studyResult1 = studyResultDao.create(clone,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		StudyResult studyResult2 = studyResultDao.create(clone,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		try {
			publixUtils
					.retrieveWorkersLastStudyResult(admin.getWorker(), study);
			Fail.fail();
		} catch (ForbiddenPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.workerNeverDidStudy(admin.getWorker(),
							study.getId()));
		}

		// Clean-up
		removeStudy(study);
		removeStudy(clone);
	}

	@Test
	public void checkRetrieveWorkersLastStudyResultAlreadyFinished()
			throws IOException, NoSuchAlgorithmException,
			ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult1 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		StudyResult studyResult2 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		publixUtils.finishStudyResult(true, null, studyResult2);
		entityManager.getTransaction().commit();

		try {
			publixUtils
					.retrieveWorkersLastStudyResult(admin.getWorker(), study);
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
		StudyModel study = importExampleStudy();
		study.getFirstComponent().setActive(false);
		addStudy(study);

		ComponentModel component = publixUtils
				.retrieveFirstActiveComponent(study);
		assertThat(component).isEqualTo(study.getComponent(2));

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveFirstActiveComponentNonActive()
			throws IOException, NoSuchAlgorithmException,
			NotFoundPublixException {
		StudyModel study = importExampleStudy();
		for (ComponentModel component : study.getComponentList()) {
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
	public void checkRetrieveNextActiveComponent() throws IOException,
			NoSuchAlgorithmException, NotFoundPublixException,
			ForbiddenReloadException {
		StudyModel study = importExampleStudy();
		for (ComponentModel component : study.getComponentList()) {
			component.setActive(false);
		}
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		publixUtils.startComponent(study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();

		ComponentModel component = publixUtils
				.retrieveNextActiveComponent(studyResult);
		// Since the 2. component is not active ...
		assertThat(component).isNull();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveComponent() throws IOException,
			NotFoundPublixException, BadRequestPublixException,
			ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		study.getLastComponent().setStudy(study);
		addStudy(study);

		ComponentModel component = publixUtils.retrieveComponent(study, study
				.getLastComponent().getId());
		assertThat(component).isEqualTo(study.getLastComponent());

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveComponentWrongId() throws IOException,
			NotFoundPublixException, BadRequestPublixException,
			ForbiddenPublixException {
		StudyModel study = importExampleStudy();
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
	public void checkRetrieveComponentWrongComponent() throws IOException,
			NotFoundPublixException, BadRequestPublixException,
			ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyModel clone = studyService.cloneStudy(study, admin);
		entityManager.getTransaction().commit();

		try {
			publixUtils.retrieveComponent(study, clone.getFirstComponent()
					.getId());
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
	public void checkRetrieveComponentNotActive() throws IOException,
			NotFoundPublixException, BadRequestPublixException,
			ForbiddenPublixException {
		StudyModel study = importExampleStudy();
		study.getFirstComponent().setActive(false);
		study.getFirstComponent().setStudy(study);
		addStudy(study);

		try {
			publixUtils.retrieveComponent(study, study.getFirstComponent()
					.getId());
			Fail.fail();
		} catch (ForbiddenPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.componentNotActive(study.getId(), study
							.getFirstComponent().getId()));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveComponentByPosition() throws IOException,
			PublixException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		ComponentModel component = publixUtils.retrieveComponentByPosition(
				study.getId(), 1);
		assertThat(component).isEqualTo(study.getFirstComponent());

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveComponentByPositionNull() throws IOException,
			PublixException {
		StudyModel study = importExampleStudy();
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
	public void checkRetrieveComponentByPositionWrong() throws IOException,
			PublixException {
		StudyModel study = importExampleStudy();
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
	public void checkRetrieveStudy() throws NotFoundPublixException,
			IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		StudyModel retrievedStudy = publixUtils.retrieveStudy(study.getId());
		assertThat(retrievedStudy).isEqualTo(study);

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRetrieveStudyNotFound() throws NotFoundPublixException,
			IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		try {
			publixUtils.retrieveStudy(999l);
			Fail.fail();
		} catch (NotFoundPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					errorMessages.studyNotExist(999l));
		}

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkComponentBelongsToStudy() throws IOException,
			PublixException {
		StudyModel study = importExampleStudy();
		study.getFirstComponent().setStudy(study);
		addStudy(study);

		publixUtils.checkComponentBelongsToStudy(study,
				study.getFirstComponent());

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkComponentBelongsToStudyFail() throws IOException,
			PublixException {
		StudyModel study = importExampleStudy();
		study.getFirstComponent().setStudy(study);
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyModel clone = studyService.cloneStudy(study, admin);
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
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		// FINISHED, ABORTED, FAIL must return true
		studyResult.setStudyState(StudyState.FINISHED);
		assertThat(publixUtils.finishedStudyAlready(admin.getWorker(), study))
				.isTrue();
		studyResult.setStudyState(StudyState.ABORTED);
		assertThat(publixUtils.finishedStudyAlready(admin.getWorker(), study))
				.isTrue();
		studyResult.setStudyState(StudyState.FAIL);
		assertThat(publixUtils.finishedStudyAlready(admin.getWorker(), study))
				.isTrue();

		// DATA_RETRIEVED, STARTED must return false
		studyResult.setStudyState(StudyState.DATA_RETRIEVED);
		assertThat(publixUtils.finishedStudyAlready(admin.getWorker(), study))
				.isFalse();
		studyResult.setStudyState(StudyState.STARTED);
		assertThat(publixUtils.finishedStudyAlready(admin.getWorker(), study))
				.isFalse();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkFinishedStudyAlreadyWrong() throws IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyModel clone = studyService.cloneStudy(study, admin);
		entityManager.getTransaction().commit();

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(clone,
				admin.getWorker());
		studyResult.setStudyState(StudyState.FINISHED);
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		assertThat(publixUtils.finishedStudyAlready(admin.getWorker(), study))
				.isFalse();

		// Clean-up
		removeStudy(study);
		removeStudy(clone);
	}

	@Test
	public void checkDidStudyAlready() throws IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		assertThat(publixUtils.didStudyAlready(admin.getWorker(), study))
				.isFalse();

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		assertThat(publixUtils.didStudyAlready(admin.getWorker(), study))
				.isTrue();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkStudyDone() throws IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		entityManager.getTransaction().commit();

		// FINISHED, ABORTED, FAIL must return true
		studyResult.setStudyState(StudyState.FINISHED);
		assertThat(publixUtils.studyDone(studyResult)).isTrue();
		studyResult.setStudyState(StudyState.ABORTED);
		assertThat(publixUtils.studyDone(studyResult)).isTrue();
		studyResult.setStudyState(StudyState.FAIL);
		assertThat(publixUtils.studyDone(studyResult)).isTrue();

		// DATA_RETRIEVED, STARTED must return false
		studyResult.setStudyState(StudyState.DATA_RETRIEVED);
		assertThat(publixUtils.studyDone(studyResult)).isFalse();
		studyResult.setStudyState(StudyState.STARTED);
		assertThat(publixUtils.studyDone(studyResult)).isFalse();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkComponentDone() throws IOException,
			ForbiddenReloadException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		ComponentResult componentResult = publixUtils.startComponent(
				study.getFirstComponent(), studyResult);
		// Have to set study manually in test - don't know why
		componentResult.getComponent().setStudy(study);
		entityManager.getTransaction().commit();

		// A component is done if state FINISHED, ABORTED, FAIL, or RELOADED
		componentResult.setComponentState(ComponentState.FINISHED);
		assertThat(publixUtils.componentDone(componentResult)).isTrue();
		componentResult.setComponentState(ComponentState.ABORTED);
		assertThat(publixUtils.componentDone(componentResult)).isTrue();
		componentResult.setComponentState(ComponentState.FAIL);
		assertThat(publixUtils.componentDone(componentResult)).isTrue();
		componentResult.setComponentState(ComponentState.RELOADED);
		assertThat(publixUtils.componentDone(componentResult)).isTrue();

		// Not done if
		componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
		assertThat(publixUtils.componentDone(componentResult)).isFalse();
		componentResult.setComponentState(ComponentState.RESULTDATA_POSTED);
		assertThat(publixUtils.componentDone(componentResult)).isFalse();
		componentResult.setComponentState(ComponentState.STARTED);
		assertThat(publixUtils.componentDone(componentResult)).isFalse();

		// Clean-up
		removeStudy(study);
	}

}
