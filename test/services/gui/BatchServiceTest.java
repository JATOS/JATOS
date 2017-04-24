package services.gui;

import static org.fest.assertions.Assertions.assertThat;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import daos.common.BatchDao;
import daos.common.GroupResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import general.TestHelper;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.JatosWorker;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;

/**
 * Tests BatchService class
 * 
 * @author Kristian Lange (2017)
 */
public class BatchServiceTest {

	private Injector injector;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private BatchDao batchDao;

	@Inject
	private StudyDao studyDao;

	@Inject
	private WorkerDao workerDao;

	@Inject
	private StudyResultDao studyResultDao;

	@Inject
	private GroupResultDao groupResultDao;

	@Inject
	private BatchService batchService;

	@Before
	public void startApp() throws Exception {
		GuiceApplicationBuilder builder = new GuiceApplicationLoader()
				.builder(new ApplicationLoader.Context(Environment.simple()));
		injector = Guice.createInjector(builder.applicationModule());
		injector.injectMembers(this);
	}

	@After
	public void stopApp() throws Exception {
		// Clean up
		testHelper.removeAllStudies();
		testHelper.removeStudyAssetsRootDir();
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	/**
	 * Tests BatchService.clone: creates a new Batch with most of the values of
	 * the given Batch
	 */
	// @Test
	public void checkClone() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		MTWorker mtWorker = new MTWorker();
		MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
		PersonalMultipleWorker personalMultipleWorker = new PersonalMultipleWorker();
		PersonalSingleWorker personalSingleWorker = new PersonalSingleWorker();
		GeneralSingleWorker generalSingleWorker = new GeneralSingleWorker();

		Batch batch = createDummyBatch(study);
		batch.setId(1111l);
		batch.setUuid("123-uid-456");
		batch.addWorker(mtWorker);
		batch.addWorker(mtSandboxWorker);
		batch.addWorker(personalSingleWorker);
		batch.addWorker(personalMultipleWorker);
		batch.addWorker(generalSingleWorker);

		// Check cloned fields that must be equal to the original
		Batch clone = batchService.clone(batch);
		assertThat(clone.getAllowedWorkerTypes())
				.isEqualTo(batch.getAllowedWorkerTypes());
		assertThat(clone.getMaxActiveMembers())
				.isEqualTo(batch.getMaxActiveMembers());
		assertThat(clone.getMaxTotalMembers())
				.isEqualTo(batch.getMaxTotalMembers());
		assertThat(clone.getMaxTotalWorkers())
				.isEqualTo(batch.getMaxTotalWorkers());
		assertThat(clone.getTitle()).isEqualTo(batch.getTitle());
		assertThat(clone.getWorkerList()).isEqualTo(batch.getWorkerList());
		assertThat(clone.getComments()).isEqualTo(batch.getComments());
		assertThat(clone.getJsonData()).isEqualTo(batch.getJsonData());

		// Check fields that weren't cloned
		assertThat(clone.getId()).isNotEqualTo(batch.getId());
		assertThat(clone.getStudy()).isNotEqualTo(batch.getStudy());
		assertThat(clone.getUuid()).isNotEqualTo(batch.getUuid());
		assertThat(clone.getBatchSessionData()).isNotEqualTo(batch.getBatchSessionData());
	}

	private Batch createDummyBatch(Study study) {
		User admin = testHelper.getAdmin();

		Batch batch = new Batch();
		batch.setActive(true);
		batch.addAllowedWorkerType(JatosWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(MTWorker.WORKER_TYPE);
		// Not necessary to add MTSandboxWorker - it's included with MTWorker
		batch.addAllowedWorkerType(GeneralSingleWorker.WORKER_TYPE);
		batch.setMaxActiveMembers(5);
		batch.setMaxTotalMembers(6);
		batch.setMaxTotalWorkers(7);
		batch.setStudy(study);
		batch.setTitle("Batch Title");
		batch.addWorker(admin.getWorker());
		return batch;
	}

	/**
	 * Tests BatchService.createAndPersistBatch(): persists a Batch in the
	 * database
	 */
	@Test
	public void checkCreateAndPersistBatch() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Batch batch = createDummyBatch(study);

		jpaApi.withTransaction(() -> {
			batchService.createAndPersistBatch(batch, study);
		});

		// Check that's persisted and ID and UUID are set
		jpaApi.withTransaction(() -> {
			Batch persistedBatch = batchDao.findById(batch.getId());
			assertThat(persistedBatch).isNotNull();
			assertThat(persistedBatch.getId()).isNotNull();
			assertThat(persistedBatch.getUuid()).isNotEmpty();
		});
	}

	/**
	 * Tests BatchService.remove(): Removes a Batch from the database
	 */
	@Test
	public void checkRemove() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Batch batch = createDummyBatch(study);

		// Persist the batch in the DB
		jpaApi.withTransaction(() -> {
			// Add a couple of workers to the batch, so we have something to
			// check afterwards
			MTWorker mtWorker = new MTWorker();
			MTSandboxWorker mtSandboxWorker = new MTSandboxWorker();
			PersonalMultipleWorker personalMultipleWorker = new PersonalMultipleWorker();
			PersonalSingleWorker personalSingleWorker = new PersonalSingleWorker();
			GeneralSingleWorker generalSingleWorker = new GeneralSingleWorker();
			workerDao.create(mtWorker);
			workerDao.create(mtSandboxWorker);
			workerDao.create(personalMultipleWorker);
			workerDao.create(personalSingleWorker);
			workerDao.create(generalSingleWorker);
			batch.addWorker(mtWorker);
			batch.addWorker(mtSandboxWorker);
			batch.addWorker(personalSingleWorker);
			batch.addWorker(personalMultipleWorker);
			batch.addWorker(generalSingleWorker);

			batchService.createAndPersistBatch(batch, study);
		});

		// Remove the batch
		jpaApi.withTransaction(() -> {
			Batch b = batchDao.findById(batch.getId());
			batchService.remove(b);
		});

		// Check the removal
		jpaApi.withTransaction(() -> {
			Batch b = batchDao.findById(batch.getId());
			Study s = studyDao.findById(study.getId());

			// The batch doesn't exist in the database anymore
			assertThat(b).isNull();

			// The study doesn't have this batch anymore
			assertThat(s.hasBatch(b)).isFalse();

			// All study results have to be removed
			assertThat(studyResultDao.findAllByBatch(batch)).isEmpty();

			// All group results have to be removed
			assertThat(groupResultDao.findAllByBatch(batch)).isEmpty();

			// Check that all Workers are gone except JatosWorkers and
			// JatosWorkers don't have the batch anymore
			batch.getWorkerList().forEach(w -> {
				Worker worker = workerDao.findById(w.getId());
				if (worker instanceof JatosWorker) {
					assertThat(worker).isNotNull();
					assertThat(worker.hasBatch(batch)).isFalse();
				} else {
					assertThat(worker).isNull();
				}
			});
		});

	}

}
