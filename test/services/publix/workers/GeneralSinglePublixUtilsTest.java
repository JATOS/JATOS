package services.publix.workers;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import models.common.workers.GeneralSingleWorker;
import services.publix.PublixUtilsTest;

/**
 * @author Kristian Lange
 */
public class GeneralSinglePublixUtilsTest
		extends PublixUtilsTest<GeneralSingleWorker> {

	private GeneralSingleErrorMessages generalSingleErrorMessages;
	private GeneralSinglePublixUtils generalSinglePublixUtils;

//	@Override
//	public void before() throws Exception {
//		super.before();
//		generalSinglePublixUtils = application.injector()
//				.instanceOf(GeneralSinglePublixUtils.class);
//		publixUtils = generalSinglePublixUtils;
//		generalSingleErrorMessages = application.injector()
//				.instanceOf(GeneralSingleErrorMessages.class);
//		errorMessages = generalSingleErrorMessages;
//	}
//
//	@Override
//	public void after() throws Exception {
//		super.after();
//	}
//
//	@Test
//	public void checkRetrieveTypedWorker()
//			throws NoSuchAlgorithmException, IOException, PublixException {
//		GeneralSingleWorker worker = new GeneralSingleWorker();
//		persistWorker(worker);
//
//		GeneralSingleWorker retrievedWorker = publixUtils
//				.retrieveTypedWorker(worker.getId());
//		assertThat(retrievedWorker.getId()).isEqualTo(worker.getId());
//	}
//
//	@Test
//	public void checkRetrieveTypedWorkerWrongType()
//			throws NoSuchAlgorithmException, IOException, PublixException {
//		try {
//			generalSinglePublixUtils
//					.retrieveTypedWorker(admin.getWorker().getId());
//			Fail.fail();
//		} catch (ForbiddenPublixException e) {
//			assertThat(e.getMessage()).isEqualTo(generalSingleErrorMessages
//					.workerNotCorrectType(admin.getWorker().getId()));
//		}
//	}

}
