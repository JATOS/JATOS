package services.publix.workers;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.publix.PublixException;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import services.publix.PublixUtilsTest;
import services.publix.workers.MTErrorMessages;
import services.publix.workers.MTPublixUtils;

/**
 * @author Kristian Lange
 */
public class MTPublixUtilsTest extends PublixUtilsTest<MTWorker> {

	private MTErrorMessages mtErrorMessages;
	private MTPublixUtils mtPublixUtils;

	@Override
	public void before() throws Exception {
		super.before();
		mtPublixUtils = application.injector().instanceOf(MTPublixUtils.class);
		publixUtils = mtPublixUtils;
		mtErrorMessages = application.injector()
				.instanceOf(MTErrorMessages.class);
		errorMessages = mtErrorMessages;
	}

	@Override
	public void after() throws Exception {
		super.before();
	}

	@Test
	public void checkRetrieveTypedWorker()
			throws NoSuchAlgorithmException, IOException, PublixException {
		MTWorker mtWorker = new MTWorker();
		persistWorker(mtWorker);
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		persistWorker(mtSandboxWorker);

		MTWorker retrievedWorker = publixUtils
				.retrieveTypedWorker(mtWorker.getId());
		assertThat(retrievedWorker.getId()).isEqualTo(mtWorker.getId());
		retrievedWorker = publixUtils
				.retrieveTypedWorker(mtSandboxWorker.getId());
		assertThat(retrievedWorker.getId()).isEqualTo(mtSandboxWorker.getId());

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
			assertThat(e.getMessage()).isEqualTo(mtErrorMessages
					.workerNotCorrectType(generalSingleWorker.getId()));
		}
	}

}
