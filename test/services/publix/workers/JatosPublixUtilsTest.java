package services.publix.workers;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;

import org.fest.assertions.Fail;
import org.junit.Test;

import controllers.publix.workers.JatosPublix;
import controllers.publix.workers.JatosPublix.JatosRun;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import models.common.User;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.JatosWorker;
import play.mvc.Http;
import services.gui.UserService;
import services.publix.PublixUtilsTest;

/**
 * @author Kristian Lange
 */
public class JatosPublixUtilsTest extends PublixUtilsTest<JatosWorker> {

	@Inject
	private JatosErrorMessages jatosErrorMessages;

	@Inject
	private JatosPublixUtils jatosPublixUtils;

	@Test
	public void checkRetrieveTypedWorker() {
		JatosWorker retrievedWorker = jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			try {
				return jatosPublixUtils
						.retrieveTypedWorker(admin.getWorker().getId());
			} catch (ForbiddenPublixException e) {
				throw new RuntimeException(e);
			}
		});

		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			assertThat(retrievedWorker.getId())
					.isEqualTo(admin.getWorker().getId());
		});
	}

	@Test
	public void checkRetrieveTypedWorkerWrongType()
			throws NoSuchAlgorithmException, IOException, PublixException {
		GeneralSingleWorker generalSingleWorker = jpaApi.withTransaction(() -> {
			GeneralSingleWorker w = new GeneralSingleWorker();
			workerDao.create(w);
			return w;
		});

		jpaApi.withTransaction(() -> {
			try {
				jatosPublixUtils.retrieveTypedWorker(generalSingleWorker.getId());
				Fail.fail();
			} catch (ForbiddenPublixException e) {
				assertThat(e.getMessage()).isEqualTo(jatosErrorMessages
						.workerNotCorrectType(generalSingleWorker.getId()));
			}
		});
	}

	@Test
	public void checkRetrieveJatosRunFromSession()
			throws ForbiddenPublixException, BadRequestPublixException {
		testHelper.mockContext();
		Http.Context.current().session().put(JatosPublix.SESSION_JATOS_RUN,
				JatosRun.RUN_STUDY.name());

		JatosRun jatosRun = jatosPublixUtils.retrieveJatosRunFromSession();

		assertThat(jatosRun).isEqualTo(JatosRun.RUN_STUDY);
	}

	@Test
	public void checkRetrieveJatosRunFromSessionFail()
			throws ForbiddenPublixException {
		testHelper.mockContext();

		try {
			jatosPublixUtils.retrieveJatosRunFromSession();
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					JatosErrorMessages.STUDY_OR_COMPONENT_NEVER_STARTED_FROM_JATOS);
		}
	}
}
