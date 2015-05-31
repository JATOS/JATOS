package publix;

import static org.fest.assertions.Assertions.assertThat;
import exceptions.publix.PublixException;
import gui.AbstractTest;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.workers.Worker;

import org.fest.assertions.Fail;
import org.junit.Test;

import persistance.StudyResultDao;
import persistance.workers.WorkerDao;

import common.Global;

import controllers.publix.PublixErrorMessages;
import controllers.publix.PublixUtils;

/**
 * @author Kristian Lange
 */
public class PublixUtilsTest<T extends Worker> extends AbstractTest {

	protected WorkerDao workerDao;
	protected StudyResultDao studyResultDao;
	protected PublixUtils<T> publixUtils;
	protected PublixErrorMessages<T> errorMessages;

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

	public void addWorker(Worker worker) {
		entityManager.getTransaction().begin();
		workerDao.create(worker);
		entityManager.getTransaction().commit();
	}

	@Test
	public void checkRetrieveWorker() throws NoSuchAlgorithmException,
			IOException, PublixException {
		// Worker is null
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
	public void checkStartComponent() {
		
	}

}
