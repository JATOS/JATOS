import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

import javax.persistence.EntityManager;

import models.StudyModel;
import models.UserDao;
import models.UserModel;

import org.apache.commons.io.FileUtils;

import play.Logger;
import play.db.jpa.JPA;
import play.db.jpa.JPAPlugin;
import play.test.FakeApplication;
import play.test.Helpers;
import scala.Option;
import services.IOUtils;
import services.JsonUtils.UploadUnmarshaller;
import services.PersistanceUtils;
import services.UserService;
import services.ZipUtil;

import com.google.inject.Inject;

import controllers.publix.StudyAssets;

/**
 * Utils class to test controllers. Set up a fake application with it's own
 * database, import a study, etc.
 * 
 * @author Kristian Lange
 */
public class ControllerTestUtils {

	private static final String CLASS_NAME = ControllerTestUtils.class
			.getSimpleName();

	protected FakeApplication application;
	protected EntityManager entityManager;
	protected UserModel admin;

	private final UserDao userDao;
	private final PersistanceUtils persistanceUtils;

	@Inject
	public ControllerTestUtils(UserDao userDao,
			PersistanceUtils persistanceUtils) {
		this.userDao = userDao;
		this.persistanceUtils = persistanceUtils;
	}

	protected void startApp() throws Exception {
		application = Helpers.fakeApplication();
		Helpers.start(application);

		Option<JPAPlugin> jpaPlugin = application.getWrappedApplication()
				.plugin(JPAPlugin.class);
		entityManager = jpaPlugin.get().em("default");
		JPA.bindForCurrentThread(entityManager);

		// Get admin (admin is automatically created during initialisation)
		admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
	}

	protected void stopApp() throws IOException {
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
		File studyZip = new File("test/assets/basic_example_study.zip");
		File tempUnzippedStudyDir = ZipUtil.unzip(studyZip);
		File[] studyFileList = IOUtils.findFiles(tempUnzippedStudyDir, "",
				IOUtils.STUDY_FILE_SUFFIX);
		File studyFile = studyFileList[0];
		UploadUnmarshaller uploadUnmarshaller = new UploadUnmarshaller();
		StudyModel importedStudy = uploadUnmarshaller.unmarshalling(studyFile,
				StudyModel.class);
		studyFile.delete();

		File[] dirArray = IOUtils.findDirectories(tempUnzippedStudyDir);
		IOUtils.moveStudyAssetsDir(dirArray[0], importedStudy.getDirName());

		tempUnzippedStudyDir.delete();
		return importedStudy;
	}

	protected synchronized StudyModel cloneAndPersistStudy(
			StudyModel studyToBeCloned) throws IOException {
		StudyModel studyClone = new StudyModel(studyToBeCloned);
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
		String passwordHash = UserService.getHashMDFive(password);
		UserModel user = new UserModel(email, name, passwordHash);
		entityManager.getTransaction().begin();
		persistanceUtils.addUser(user);
		entityManager.getTransaction().commit();
		return user;
	}

	protected synchronized void removeStudy(StudyModel study)
			throws IOException {
		IOUtils.removeStudyAssetsDir(study.getDirName());
		entityManager.getTransaction().begin();
		persistanceUtils.removeStudy(study);
		entityManager.getTransaction().commit();
	}

	protected synchronized void addStudy(StudyModel study) {
		entityManager.getTransaction().begin();
		persistanceUtils.addStudy(study, admin);
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
		StudyModel.findById(studyClone.getId()).removeMember(member);
		entityManager.getTransaction().commit();
	}

}
