package gui;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

import javax.persistence.EntityManager;

import models.StudyModel;
import models.UserModel;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;

import persistance.ComponentDao;
import persistance.StudyDao;
import persistance.UserDao;
import play.GlobalSettings;
import play.Logger;
import play.db.jpa.JPA;
import play.db.jpa.JPAPlugin;
import play.test.FakeApplication;
import play.test.Helpers;
import scala.Option;
import services.gui.UserService;
import utils.IOUtils;
import utils.JsonUtils;
import utils.ZipUtil;

import common.Global;

import controllers.publix.StudyAssets;

/**
 * Abstract class for a controller test. Starts fake application.
 * 
 * @author Kristian Lange
 */
public abstract class AbstractGuiTest {

	private static final String BASIC_EXAMPLE_STUDY_ZIP = "test/assets/basic_example_study.zip";

	private static final String CLASS_NAME = AbstractGuiTest.class
			.getSimpleName();

	private static final String TEST_COMPONENT_JAC_PATH = "test/assets/hello_world.jac";
	private static final String TEST_COMPONENT_BKP_JAC_FILENAME = "hello_world_bkp.jac";

	protected FakeApplication application;
	protected EntityManager entityManager;
	protected JsonUtils jsonUtils;
	protected UserService userService;
	protected UserDao userDao;
	protected StudyDao studyDao;
	protected ComponentDao componentDao;
	protected UserModel admin;

	public abstract void before() throws Exception;

	public abstract void after() throws Exception;

	@Before
	public void startApp() throws Exception {
		GlobalSettings global = (GlobalSettings) Class.forName("common.Global")
				.newInstance();

		application = Helpers.fakeApplication(global);
		Helpers.start(application);

		// Use Guice dependency injection
		jsonUtils = Global.INJECTOR.getInstance(JsonUtils.class);
		userService = Global.INJECTOR.getInstance(UserService.class);
		userDao = Global.INJECTOR.getInstance(UserDao.class);
		studyDao = Global.INJECTOR.getInstance(StudyDao.class);
		componentDao = Global.INJECTOR.getInstance(ComponentDao.class);

		Option<JPAPlugin> jpaPlugin = application.getWrappedApplication()
				.plugin(JPAPlugin.class);
		entityManager = jpaPlugin.get().em("default");
		JPA.bindForCurrentThread(entityManager);

		// Get admin (admin is automatically created during initialisation)
		admin = userDao.findByEmail(UserService.ADMIN_EMAIL);

		before();
	}

	@After
	public void stopApp() throws Exception {
		after();

		entityManager.close();
		JPA.bindForCurrentThread(null);
		removeStudyAssetsRootDir();
		Helpers.stop(application);
	}

	protected static void removeStudyAssetsRootDir() throws IOException {
		File assetsRoot = new File(StudyAssets.STUDY_ASSETS_ROOT_PATH);
		if (assetsRoot.list().length > 0) {
			Logger.warn(CLASS_NAME
					+ ".removeStudyAssetsRootDir: Study assets root directory "
					+ StudyAssets.STUDY_ASSETS_ROOT_PATH
					+ " is not empty after finishing testing. This should not happen.");
		}
		FileUtils.deleteDirectory(assetsRoot);
	}

	protected StudyModel importExampleStudy() throws NoSuchAlgorithmException,
			IOException {
		File studyZip = new File(BASIC_EXAMPLE_STUDY_ZIP);
		File tempUnzippedStudyDir = ZipUtil.unzip(studyZip);
		File[] studyFileList = IOUtils.findFiles(tempUnzippedStudyDir, "",
				IOUtils.STUDY_FILE_SUFFIX);
		File studyFile = studyFileList[0];
		StudyModel importedStudy = new JsonUtils.UploadUnmarshaller()
				.unmarshalling(studyFile, StudyModel.class);
		studyFile.delete();

		File[] dirArray = IOUtils.findDirectories(tempUnzippedStudyDir);
		IOUtils.moveStudyAssetsDir(dirArray[0], importedStudy.getDirName());

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

	protected synchronized StudyModel cloneAndPersistStudy(
			StudyModel studyToBeCloned) throws IOException {
		StudyModel studyClone = studyToBeCloned.clone();
		String destDirName;
		destDirName = IOUtils
				.cloneStudyAssetsDirectory(studyClone.getDirName());
		studyClone.setDirName(destDirName);
		addStudy(studyClone);
		return studyClone;
	}

	protected synchronized UserModel createAndPersistUser(String email,
			String name, String password) throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		String passwordHash = userService.getHashMDFive(password);
		UserModel user = new UserModel(email, name, passwordHash);
		entityManager.getTransaction().begin();
		userDao.create(user);
		entityManager.getTransaction().commit();
		return user;
	}

	protected synchronized void removeStudy(StudyModel study)
			throws IOException {
		IOUtils.removeStudyAssetsDir(study.getDirName());
		entityManager.getTransaction().begin();
		studyDao.remove(study);
		entityManager.getTransaction().commit();
	}

	protected synchronized void addStudy(StudyModel study) {
		entityManager.getTransaction().begin();
		studyDao.create(study, admin);
		entityManager.getTransaction().commit();
	}

	protected synchronized void lockStudy(StudyModel study) {
		entityManager.getTransaction().begin();
		study.setLocked(true);
		entityManager.getTransaction().commit();
	}

	protected synchronized void removeMember(StudyModel studyClone,
			UserModel member) {
		entityManager.getTransaction().begin();
		studyDao.findById(studyClone.getId()).removeMember(member);
		entityManager.getTransaction().commit();
	}

}
