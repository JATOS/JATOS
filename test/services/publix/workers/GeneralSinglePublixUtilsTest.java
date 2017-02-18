package services.publix.workers;

import static org.fest.assertions.Assertions.assertThat;

import javax.inject.Inject;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.publix.ForbiddenPublixException;
import models.common.User;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.Worker;
import services.gui.UserService;
import services.publix.PublixUtilsTest;

/**
 * Tests class GeneralSinglePublixUtils. Most test cases are in parent class
 * PublixUtilsTest.
 * 
 * @author Kristian Lange
 */
public class GeneralSinglePublixUtilsTest
		extends PublixUtilsTest<GeneralSingleWorker> {

	@Inject
	private GeneralSingleErrorMessages generalSingleErrorMessages;

	@Inject
	private GeneralSinglePublixUtils generalSinglePublixUtils;

	@Test
	public void checkRetrieveTypedWorker() {
		Worker worker = jpaApi.withTransaction(() -> {
			GeneralSingleWorker w = new GeneralSingleWorker();
			workerDao.create(w);
			return w;
		});

		jpaApi.withTransaction(() -> {
			try {
				GeneralSingleWorker retrievedWorker = generalSinglePublixUtils
						.retrieveTypedWorker(worker.getId());
				assertThat(retrievedWorker.getId()).isEqualTo(worker.getId());
			} catch (ForbiddenPublixException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	public void checkRetrieveTypedWorkerWrongType() {
		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			try {
				generalSinglePublixUtils
						.retrieveTypedWorker(admin.getWorker().getId());
				Fail.fail();
			} catch (ForbiddenPublixException e) {
				assertThat(e.getMessage()).isEqualTo(generalSingleErrorMessages
						.workerNotCorrectType(admin.getWorker().getId()));
			}
		});
	}

}
