package publix.services.personal_multiple;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.publix.PublixException;
import models.common.workers.PersonalMultipleWorker;
import publix.services.PublixUtilsTest;
import services.publix.personal_multiple.PersonalMultipleErrorMessages;
import services.publix.personal_multiple.PersonalMultiplePublixUtils;

/**
 * @author Kristian Lange
 */
public class PersonalMultiplePublixUtilsTest
		extends PublixUtilsTest<PersonalMultipleWorker> {

	private PersonalMultipleErrorMessages personalMultipleErrorMessages;
	private PersonalMultiplePublixUtils personalMultiplePublixUtils;

	@Override
	public void before() throws Exception {
		super.before();
		personalMultiplePublixUtils = application.injector()
				.instanceOf(PersonalMultiplePublixUtils.class);
		publixUtils = personalMultiplePublixUtils;
		personalMultipleErrorMessages = application.injector()
				.instanceOf(PersonalMultipleErrorMessages.class);
		errorMessages = personalMultipleErrorMessages;
	}

	@Override
	public void after() throws Exception {
		super.before();
	}

	@Test
	public void checkRetrieveTypedWorker()
			throws NoSuchAlgorithmException, IOException, PublixException {
		PersonalMultipleWorker worker = new PersonalMultipleWorker();
		addWorker(worker);

		PersonalMultipleWorker retrievedWorker = publixUtils
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
			assertThat(e.getMessage()).isEqualTo(personalMultipleErrorMessages
					.workerNotCorrectType(admin.getWorker().getId()));
		}
	}

}
