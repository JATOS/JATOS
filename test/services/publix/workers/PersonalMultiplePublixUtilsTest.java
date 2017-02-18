package services.publix.workers;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.PersonalMultipleWorker;
import services.publix.PublixUtilsTest;
import services.publix.workers.PersonalMultipleErrorMessages;
import services.publix.workers.PersonalMultiplePublixUtils;

/**
 * @author Kristian Lange
 */
public class PersonalMultiplePublixUtilsTest
		extends PublixUtilsTest<PersonalMultipleWorker> {

	@Inject
	private PersonalMultipleErrorMessages personalMultipleErrorMessages;

	@Inject
	private PersonalMultiplePublixUtils personalMultiplePublixUtils;

	@Test
	public void checkRetrieveTypedWorker()
			throws NoSuchAlgorithmException, IOException, PublixException {
		PersonalMultipleWorker worker = jpaApi.withTransaction(() -> {
			PersonalMultipleWorker w = new PersonalMultipleWorker();
			workerDao.create(w);
			return w;
		});

		jpaApi.withTransaction(() -> {
			try {
				PersonalMultipleWorker retrievedWorker = personalMultiplePublixUtils
						.retrieveTypedWorker(worker.getId().toString());
				assertThat(retrievedWorker.getId()).isEqualTo(worker.getId());
			} catch (ForbiddenPublixException e) {
				throw new RuntimeException(e);
			}
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
				personalMultiplePublixUtils.retrieveTypedWorker(
						generalSingleWorker.getId().toString());
				Fail.fail();
			} catch (PublixException e) {
				assertThat(e.getMessage()).isEqualTo(
						personalMultipleErrorMessages.workerNotCorrectType(
								generalSingleWorker.getId()));
			}
		});
	}

}
