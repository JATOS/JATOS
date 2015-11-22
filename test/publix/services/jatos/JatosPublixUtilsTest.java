package publix.services.jatos;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.fest.assertions.Fail;
import org.junit.Test;

import controllers.publix.jatos.JatosPublix;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.JatosWorker;
import play.mvc.Http;
import publix.services.PublixUtilsTest;
import services.publix.jatos.JatosErrorMessages;
import services.publix.jatos.JatosPublixUtils;

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
				.retrieveTypedWorker(admin.getWorker().getId().toString());
		assertThat(retrievedWorker.getId())
				.isEqualTo(admin.getWorker().getId());
	}

	@Test
	public void checkRetrieveTypedWorkerWrongType()
			throws NoSuchAlgorithmException, IOException, PublixException {
		GeneralSingleWorker generalSingleWorker = new GeneralSingleWorker();
		addWorker(generalSingleWorker);

		try {
			publixUtils.retrieveTypedWorker(
					generalSingleWorker.getId().toString());
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(jatosErrorMessages
					.workerNotCorrectType(generalSingleWorker.getId()));
		}
	}

	@Test
	public void checkRetrieveJatosShowFromSession()
			throws ForbiddenPublixException {
		mockContext();
		Http.Context.current().session().put(JatosPublix.JATOS_RUN,
				JatosPublix.RUN_STUDY);

		String jatosShow = jatosPublixUtils.retrieveJatosShowFromSession();

		assertThat(jatosShow).isEqualTo(JatosPublix.RUN_STUDY);
	}

	@Test
	public void checkRetrieveJatosShowFromSessionFail()
			throws ForbiddenPublixException {
		mockContext();

		try {
			jatosPublixUtils.retrieveJatosShowFromSession();
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					JatosErrorMessages.STUDY_OR_COMPONENT_NEVER_STARTED_FROM_JATOS);
		}
	}
}
