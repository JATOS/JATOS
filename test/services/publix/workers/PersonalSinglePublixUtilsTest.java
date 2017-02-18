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
import models.common.workers.PersonalSingleWorker;
import services.publix.PublixUtilsTest;
import services.publix.workers.PersonalSingleErrorMessages;
import services.publix.workers.PersonalSinglePublixUtils;

/**
 * @author Kristian Lange
 */
public class PersonalSinglePublixUtilsTest
		extends PublixUtilsTest<PersonalSingleWorker> {

	@Inject
	private PersonalSingleErrorMessages personalSingleErrorMessages;

	@Inject
	private PersonalSinglePublixUtils personalSinglePublixUtils;

	@Test
	public void checkRetrieveTypedWorker()
			throws NoSuchAlgorithmException, IOException, PublixException {
		PersonalSingleWorker worker = jpaApi.withTransaction(() -> {
			PersonalSingleWorker w = new PersonalSingleWorker();
			workerDao.create(w);
			return w;
		});

		jpaApi.withTransaction(() -> {
			try {
				PersonalSingleWorker retrievedWorker = personalSinglePublixUtils
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
				personalSinglePublixUtils.retrieveTypedWorker(
						generalSingleWorker.getId().toString());
				Fail.fail();
			} catch (PublixException e) {
				assertThat(e.getMessage()).isEqualTo(personalSingleErrorMessages
						.workerNotCorrectType(generalSingleWorker.getId()));
			}
		});
	}

}
