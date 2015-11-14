package publix.services.mt;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.common.workers.GeneralSingleWorker;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;

import org.fest.assertions.Fail;
import org.junit.Test;

import publix.PublixTestGlobal;
import publix.services.PublixUtilsTest;
import services.publix.mt.MTErrorMessages;
import services.publix.mt.MTPublixUtils;
import exceptions.publix.PublixException;

/**
 * @author Kristian Lange
 */
public class MTPublixUtilsTest extends PublixUtilsTest<MTWorker> {

	private MTErrorMessages mtErrorMessages;
	private MTPublixUtils mtPublixUtils;

	@Override
	public void before() throws Exception {
		super.before();
		mtPublixUtils = PublixTestGlobal.INJECTOR.getInstance(MTPublixUtils.class);
		publixUtils = mtPublixUtils;
		mtErrorMessages = PublixTestGlobal.INJECTOR.getInstance(MTErrorMessages.class);
		errorMessages = mtErrorMessages;
	}

	@Override
	public void after() throws Exception {
		super.before();
	}

	@Test
	public void checkRetrieveTypedWorker() throws NoSuchAlgorithmException,
			IOException, PublixException {
		MTWorker mtWorker = new MTWorker();
		addWorker(mtWorker);
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		addWorker(mtSandboxWorker);

		MTWorker retrievedWorker = publixUtils.retrieveTypedWorker(mtWorker
				.getId().toString());
		assertThat(retrievedWorker.getId()).isEqualTo(mtWorker.getId());
		retrievedWorker = publixUtils.retrieveTypedWorker(mtSandboxWorker
				.getId().toString());
		assertThat(retrievedWorker.getId()).isEqualTo(mtSandboxWorker.getId());

	}

	@Test
	public void checkRetrieveTypedWorkerWrongType()
			throws NoSuchAlgorithmException, IOException, PublixException {
		GeneralSingleWorker generalSingleWorker = new GeneralSingleWorker();
		addWorker(generalSingleWorker);

		try {
			publixUtils.retrieveTypedWorker(generalSingleWorker.getId()
					.toString());
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					mtErrorMessages.workerNotCorrectType(generalSingleWorker
							.getId()));
		}
	}

}
