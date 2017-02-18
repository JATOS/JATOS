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
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import services.publix.PublixUtilsTest;

/**
 * @author Kristian Lange
 */
public class MTPublixUtilsTest extends PublixUtilsTest<MTWorker> {

	@Inject
	private MTErrorMessages mtErrorMessages;

	@Inject
	private MTPublixUtils mtPublixUtils;

	@Test
	public void checkRetrieveTypedWorker()
			throws NoSuchAlgorithmException, IOException, PublixException {

		MTWorker mtWorker = jpaApi.withTransaction(() -> {
			MTWorker w = new MTWorker();
			workerDao.create(w);
			return w;
		});
		MTSandboxWorker mtSandboxWorker = jpaApi.withTransaction(() -> {
			MTSandboxWorker w = new MTSandboxWorker();
			workerDao.create(w);
			return w;
		});

		jpaApi.withTransaction(() -> {
			try {
				MTWorker retrievedWorker = mtPublixUtils
						.retrieveTypedWorker(mtWorker.getId());
				assertThat(retrievedWorker.getId()).isEqualTo(mtWorker.getId());
				retrievedWorker = mtPublixUtils
						.retrieveTypedWorker(mtSandboxWorker.getId());
				assertThat(retrievedWorker.getId())
						.isEqualTo(mtSandboxWorker.getId());
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
				mtPublixUtils.retrieveTypedWorker(generalSingleWorker.getId());
				Fail.fail();
			} catch (ForbiddenPublixException e) {
				assertThat(e.getMessage()).isEqualTo(mtErrorMessages
						.workerNotCorrectType(generalSingleWorker.getId()));
			}
		});
	}

}
