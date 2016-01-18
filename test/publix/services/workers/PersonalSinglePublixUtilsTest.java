package publix.services.workers;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.publix.PublixException;
import models.common.workers.PersonalSingleWorker;
import publix.services.PublixUtilsTest;
import services.publix.workers.PersonalSingleErrorMessages;
import services.publix.workers.PersonalSinglePublixUtils;

/**
 * @author Kristian Lange
 */
public class PersonalSinglePublixUtilsTest
		extends PublixUtilsTest<PersonalSingleWorker> {

	private PersonalSingleErrorMessages personalSingleErrorMessages;
	private PersonalSinglePublixUtils personalSinglePublixUtils;

	@Override
	public void before() throws Exception {
		super.before();
		personalSinglePublixUtils = application.injector()
				.instanceOf(PersonalSinglePublixUtils.class);
		publixUtils = personalSinglePublixUtils;
		personalSingleErrorMessages = application.injector()
				.instanceOf(PersonalSingleErrorMessages.class);
		errorMessages = personalSingleErrorMessages;
	}

	@Override
	public void after() throws Exception {
		super.before();
	}

	@Test
	public void checkRetrieveTypedWorker()
			throws NoSuchAlgorithmException, IOException, PublixException {
		PersonalSingleWorker worker = new PersonalSingleWorker();
		persistWorker(worker);

		PersonalSingleWorker retrievedWorker = publixUtils
				.retrieveTypedWorker(worker.getId().toString());
		assertThat(retrievedWorker.getId()).isEqualTo(worker.getId());
	}

	@Test
	public void checkRetrieveTypedWorkerWrongType()
			throws NoSuchAlgorithmException, IOException, PublixException {
		try {
			publixUtils
					.retrieveTypedWorker(admin.getWorker().getId().toString());
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(personalSingleErrorMessages
					.workerNotCorrectType(admin.getWorker().getId()));
		}
	}

}
