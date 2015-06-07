package publix.services.mt;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.workers.GeneralSingleWorker;
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;

import org.fest.assertions.Fail;
import org.junit.Test;

import publix.exceptions.PublixException;
import publix.services.PublixServiceTest;

import common.Global;

/**
 * @author Kristian Lange
 */
public class MTPublixUtilsTest extends PublixServiceTest<MTWorker> {

	private MTErrorMessages mtErrorMessages;
	private MTPublixUtils mtPublixUtils;

	@Override
	public void before() throws Exception {
		super.before();
		mtPublixUtils = Global.INJECTOR.getInstance(MTPublixUtils.class);
		publixUtils = mtPublixUtils;
		mtErrorMessages = Global.INJECTOR.getInstance(MTErrorMessages.class);
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
