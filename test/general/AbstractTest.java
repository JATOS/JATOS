package general;

import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

import javax.persistence.EntityManager;

import org.apache.commons.io.FileUtils;
import org.h2.tools.Server;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import general.common.Common;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.User;
import models.common.workers.Worker;
import play.Application;
import play.Environment;
import play.Logger;
import play.Mode;
import play.api.mvc.RequestHeader;
import play.db.jpa.JPA;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.test.FakeApplication;
import play.test.Helpers;
import play.test.TestServer;
import services.gui.ComponentService;
import services.gui.ResultService;
import services.gui.StudyService;
import services.gui.UserService;
import utils.common.ComponentCloner;
import utils.common.HashUtils;
import utils.common.IOUtils;
import utils.common.StudyCloner;
import utils.common.StudyUploadUnmarshaller;
import utils.common.UploadUnmarshaller;
import utils.common.ZipUtil;

/**
 * Abstract class for tests. Starts fake application and an in-memory DB.
 * 
 * @author Kristian Lange
 */
public abstract class AbstractTest {

	private static final String CLASS_NAME = AbstractTest.class.getSimpleName();
	private static final String BASIC_EXAMPLE_STUDY_ZIP = "test/resources/basic_example_study.zip";
	private static final String TEST_COMPONENT_JAC_PATH = "test/resources/quit_button.jac";
	private static final String TEST_COMPONENT_BKP_JAC_FILENAME = "quit_button_bkp.jac";

	// All dependency injected
	protected static TestServer server;
	protected Application application;
	protected UserService userService;
	protected StudyService studyService;
	protected ComponentService componentService;
	protected ResultService resultService;
	protected UserDao userDao;
	protected StudyDao studyDao;
	protected ComponentDao componentDao;
	protected WorkerDao workerDao;
	protected StudyResultDao studyResultDao;
	protected ComponentResultDao componentResultDao;
	protected StudyCloner studyCloner;
	protected ComponentCloner componentCloner;
	protected IOUtils ioUtils;
	protected Common common;
	protected JPAApi jpa;

	// All not dependency injected
	private static Server dbH2Server;
	protected User admin;
	protected EntityManager entityManager;

	public abstract void before() throws Exception;

	public abstract void after() throws Exception;

	@Before
	public void startApp() throws Exception {
		ClassLoader classLoader = FakeApplication.class.getClassLoader();
		application = new GuiceApplicationBuilder().in(
				new Environment(new File(System.getProperty("java.io.tmpdir")),
						classLoader, Mode.TEST))
				.build();
		Helpers.start(application);

		// Use Guice dependency injection and bind manually
		jpa = application.injector().instanceOf(JPAApi.class);
		userService = application.injector().instanceOf(UserService.class);
		studyService = application.injector().instanceOf(StudyService.class);
		componentService = application.injector()
				.instanceOf(ComponentService.class);
		resultService = application.injector().instanceOf(ResultService.class);
		userDao = application.injector().instanceOf(UserDao.class);
		studyDao = application.injector().instanceOf(StudyDao.class);
		componentDao = application.injector().instanceOf(ComponentDao.class);
		workerDao = application.injector().instanceOf(WorkerDao.class);
		studyResultDao = application.injector()
				.instanceOf(StudyResultDao.class);
		componentResultDao = application.injector()
				.instanceOf(ComponentResultDao.class);
		studyCloner = application.injector().instanceOf(StudyCloner.class);
		componentCloner = application.injector()
				.instanceOf(ComponentCloner.class);
		common = application.injector().instanceOf(Common.class);
		ioUtils = application.injector().instanceOf(IOUtils.class);

		entityManager = jpa.em("default");
		mockContext();
		checkAdmin();

		// before() is implemented in the concrete class
		before();

		// Have to bind EntityManager again - don't know why
		JPA.bindForSync(entityManager);
	}

	@After
	public void stopApp() throws Exception {
		after();
		if (entityManager.isOpen()) {
			entityManager.close();
		}
		removeStudyAssetsRootDir();
		Helpers.stop(application);
	}

	@BeforeClass
	public static void start() throws SQLException {
		server = Helpers.testServer();
		Helpers.start(server);
		dbH2Server = Server.createTcpServer().start();
		System.out.println(
				"URL: jdbc:h2:" + dbH2Server.getURL() + "/mem:test/jatos");
	}

	@AfterClass
	public static void stop() {
		Helpers.stop(server);
		dbH2Server.stop();
	}

	/**
	 * Mocks Play's Http.Context
	 */
	protected void mockContext() {
		Map<String, String> flashData = Collections.emptyMap();
		Map<String, Object> argData = Collections.emptyMap();
		Long id = 2L;
		RequestHeader header = mock(RequestHeader.class);
		Http.Request request = mock(Http.Request.class);
		Http.Context context = new Http.Context(id, header, request, flashData,
				flashData, argData);
		Http.Context.current.set(context);
		JPA.bindForSync(entityManager);
	}

	private void checkAdmin() {
		entityManager.getTransaction().begin();
		admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
		if (admin == null) {
			admin = userService.createAdmin();
		}
		entityManager.getTransaction().commit();
	}

	protected void removeStudyAssetsRootDir() throws IOException {
		File assetsRoot = new File(common.getStudyAssetsRootPath());
		if (assetsRoot.list() != null && assetsRoot.list().length > 0) {
			Logger.warn(CLASS_NAME
					+ ".removeStudyAssetsRootDir: Study assets root directory "
					+ common.getStudyAssetsRootPath()
					+ " is not empty after finishing testing. This should not happen.");
		}
		FileUtils.deleteDirectory(assetsRoot);
	}

	protected Study importExampleStudy() throws IOException {
		File studyZip = new File(BASIC_EXAMPLE_STUDY_ZIP);
		File tempUnzippedStudyDir = ZipUtil.unzip(studyZip);
		File[] studyFileList = ioUtils.findFiles(tempUnzippedStudyDir, "",
				IOUtils.STUDY_FILE_SUFFIX);
		File studyFile = studyFileList[0];
		UploadUnmarshaller<Study> uploadUnmarshaller = application.injector()
				.instanceOf(StudyUploadUnmarshaller.class);
		Study importedStudy = uploadUnmarshaller.unmarshalling(studyFile);
		studyFile.delete();

		File[] dirArray = ioUtils.findDirectories(tempUnzippedStudyDir);
		ioUtils.moveStudyAssetsDir(dirArray[0], importedStudy.getDirName());

		tempUnzippedStudyDir.delete();
		return importedStudy;
	}

	protected synchronized File getExampleStudyFile() throws IOException {
		File studyFile = new File(BASIC_EXAMPLE_STUDY_ZIP);
		File studyFileBkp = new File(System.getProperty("java.io.tmpdir"),
				BASIC_EXAMPLE_STUDY_ZIP);
		FileUtils.copyFile(studyFile, studyFileBkp);
		return studyFileBkp;
	}

	/**
	 * Makes a backup of our component file
	 */
	protected synchronized File getExampleComponentFile() throws IOException {
		File componentFile = new File(TEST_COMPONENT_JAC_PATH);
		File componentFileBkp = new File(System.getProperty("java.io.tmpdir"),
				TEST_COMPONENT_BKP_JAC_FILENAME);
		FileUtils.copyFile(componentFile, componentFileBkp);
		return componentFileBkp;
	}

	protected synchronized Study cloneAndPersistStudy(Study studyToBeCloned)
			throws IOException {
		entityManager.getTransaction().begin();
		Study studyClone = studyCloner.clone(studyToBeCloned);
		studyDao.create(studyClone, admin);
		entityManager.getTransaction().commit();
		return studyClone;
	}

	protected synchronized User createAndPersistUser(String email, String name,
			String password) {
		String passwordHash = HashUtils.getHashMDFive(password);
		User user = new User(email, name, passwordHash);
		entityManager.getTransaction().begin();
		userDao.create(user);
		entityManager.getTransaction().commit();
		return user;
	}

	protected synchronized void removeStudy(Study study) throws IOException {
		ioUtils.removeStudyAssetsDir(study.getDirName());
		entityManager.getTransaction().begin();
		studyDao.remove(study);
		entityManager.getTransaction().commit();
	}

	protected synchronized void addStudy(Study study) {
		entityManager.getTransaction().begin();
		studyDao.create(study, admin);
		entityManager.getTransaction().commit();
	}

	protected synchronized void lockStudy(Study study) {
		entityManager.getTransaction().begin();
		study.setLocked(true);
		entityManager.getTransaction().commit();
	}

	protected synchronized void removeUser(Study studyClone, User user) {
		entityManager.getTransaction().begin();
		studyDao.findById(studyClone.getId()).removeUser(user);
		entityManager.getTransaction().commit();
	}

	protected void addWorker(Worker worker) {
		entityManager.getTransaction().begin();
		workerDao.create(worker);
		entityManager.getTransaction().commit();
	}

	protected void addStudyResult(Study study, Worker worker,
			StudyState state) {
		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(study, worker);
		studyResult.setStudyState(state);
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(worker);
		entityManager.getTransaction().commit();
	}

}
