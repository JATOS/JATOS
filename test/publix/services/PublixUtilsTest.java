package publix.services;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.ComponentResult;
import models.ComponentResult.ComponentState;
import models.StudyModel;
import models.StudyResult;
import models.workers.Worker;

import org.fest.assertions.Fail;
import org.junit.Test;

import persistance.StudyResultDao;
import persistance.workers.WorkerDao;
import publix.exceptions.ForbiddenReloadException;
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

		// Start the same component a second time
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

	private void createTwoStudyResults(StudyModel study)
			throws ForbiddenReloadException {
		entityManager.getTransaction().begin();
		StudyResult studyResult1 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult1.setWorker(admin.getWorker());
		ComponentResult componentResult11 = publixUtils.startComponent(
				study.getFirstComponent(), studyResult1);
		componentResult11
				.setData("1. StudyResult, 1. Component, 1. ComponentResult");
		ComponentResult componentResult12 = publixUtils.startComponent(
				study.getFirstComponent(), studyResult1);
		componentResult12
				.setData("1. StudyResult, 1. Component, 2. ComponentResult");

		StudyResult studyResult2 = studyResultDao.create(study,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult2.setWorker(admin.getWorker());
		ComponentResult componentResult211 = publixUtils.startComponent(
				study.getFirstComponent(), studyResult2);
		componentResult211
				.setData("2. StudyResult, 1. Component, 1. ComponentResult");
		ComponentResult componentResult212 = publixUtils.startComponent(
				study.getFirstComponent(), studyResult2);
		componentResult212
				.setData("2. StudyResult, 1. Component, 2. ComponentResult");
		ComponentResult componentResult221 = publixUtils.startComponent(
				study.getComponent(2), studyResult2);
		componentResult221
				.setData("2. StudyResult, 2. Component, 1. ComponentResult");
		ComponentResult componentResult222 = publixUtils.startComponent(
				study.getComponent(2), studyResult2);
		componentResult222
				.setData("2. StudyResult, 2. Component, 2. ComponentResult");

		// Have to set study manually in test - don't know why
		componentResult11.getComponent().setStudy(study);
		componentResult12.getComponent().setStudy(study);
		componentResult211.getComponent().setStudy(study);
		componentResult212.getComponent().setStudy(study);
		componentResult221.getComponent().setStudy(study);
		componentResult222.getComponent().setStudy(study);
		entityManager.getTransaction().commit();
	}

}
