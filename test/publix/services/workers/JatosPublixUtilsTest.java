package publix.services.workers;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.fest.assertions.Fail;
import org.junit.Test;

import controllers.publix.workers.JatosPublix;
import controllers.publix.workers.JatosPublix.JatosRun;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.JatosWorker;
import play.mvc.Http;
import publix.services.PublixUtilsTest;
import services.publix.workers.JatosErrorMessages;
import services.publix.workers.JatosPublixUtils;

/**
 * @author Kristian Lange
 */
public class JatosPublixUtilsTest extends PublixUtilsTest<JatosWorker> {

	private JatosErrorMessages jatosErrorMessages;
	private JatosPublixUtils jatosPublixUtils;

	@Override
	public void before() throws Exception {
		super.before();
		jatosPublixUtils = application.injector()
				.instanceOf(JatosPublixUtils.class);
		publixUtils = jatosPublixUtils;
		jatosErrorMessages = application.injector()
				.instanceOf(JatosErrorMessages.class);
		errorMessages = jatosErrorMessages;
	}

	@Override
	public void after() throws Exception {
		super.before();
	}

	@Test
	public void checkRetrieveTypedWorker()
			throws NoSuchAlgorithmException, IOException, PublixException {
		JatosWorker retrievedWorker = publixUtils
				.retrieveTypedWorker(admin.getWorker().getId());
		assertThat(retrievedWorker.getId())
				.isEqualTo(admin.getWorker().getId());
	}

	@Test
	public void checkRetrieveTypedWorkerWrongType()
			throws NoSuchAlgorithmException, IOException, PublixException {
		GeneralSingleWorker generalSingleWorker = new GeneralSingleWorker();
		persistWorker(generalSingleWorker);

		try {
			publixUtils.retrieveTypedWorker(
					generalSingleWorker.getId());
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(jatosErrorMessages
					.workerNotCorrectType(generalSingleWorker.getId()));
		}
	}

	@Test
	public void checkRetrieveJatosRunFromSession()
			throws ForbiddenPublixException, BadRequestPublixException {
		mockContext();
		Http.Context.current().session().put(JatosPublix.SESSION_JATOS_RUN,
				JatosRun.RUN_STUDY.name());

		JatosRun jatosRun = jatosPublixUtils.retrieveJatosRunFromSession();

		assertThat(jatosRun).isEqualTo(JatosRun.RUN_STUDY);
	}

	@Test
	public void checkRetrieveJatosRunFromSessionFail()
			throws ForbiddenPublixException {
		mockContext();

		try {
			jatosPublixUtils.retrieveJatosRunFromSession();
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					JatosErrorMessages.STUDY_OR_COMPONENT_NEVER_STARTED_FROM_JATOS);
		}
	}
}
