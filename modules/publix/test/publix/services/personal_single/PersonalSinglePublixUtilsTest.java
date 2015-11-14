package publix.services.personal_single;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.common.workers.PersonalSingleWorker;

import org.fest.assertions.Fail;
import org.junit.Test;

import publix.PublixTestGlobal;
import publix.services.PublixUtilsTest;
import services.publix.personal_single.PersonalSingleErrorMessages;
import services.publix.personal_single.PersonalSinglePublixUtils;
import exceptions.publix.PublixException;

/**
 * @author Kristian Lange
 */
public class PersonalSinglePublixUtilsTest extends
		PublixUtilsTest<PersonalSingleWorker> {

	private PersonalSingleErrorMessages personalSingleErrorMessages;
	private PersonalSinglePublixUtils personalSinglePublixUtils;

	@Override
	public void before() throws Exception {
		super.before();
		personalSinglePublixUtils = PublixTestGlobal.INJECTOR
				.getInstance(PersonalSinglePublixUtils.class);
		publixUtils = personalSinglePublixUtils;
		personalSingleErrorMessages = PublixTestGlobal.INJECTOR
				.getInstance(PersonalSingleErrorMessages.class);
		errorMessages = personalSingleErrorMessages;
	}

	@Override
	public void after() throws Exception {
		super.before();
	}

	@Test
	public void checkRetrieveTypedWorker() throws NoSuchAlgorithmException,
			IOException, PublixException {
		PersonalSingleWorker worker = new PersonalSingleWorker();
		addWorker(worker);

		PersonalSingleWorker retrievedWorker = publixUtils
				.retrieveTypedWorker(worker.getId().toString());
		assertThat(retrievedWorker.getId()).isEqualTo(worker.getId());
	}

	@Test
	public void checkRetrieveTypedWorkerWrongType()
			throws NoSuchAlgorithmException, IOException, PublixException {
		try {
			publixUtils.retrieveTypedWorker(admin.getWorker().getId()
					.toString());
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					personalSingleErrorMessages.workerNotCorrectType(admin
							.getWorker().getId()));
		}
	}

}
