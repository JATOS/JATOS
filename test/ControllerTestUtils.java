import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.persistence.EntityManager;

import models.StudyModel;
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

	protected void startApp() throws Exception {
		application = Helpers.fakeApplication();
		Helpers.start(application);

		Option<JPAPlugin> jpaPlugin = application.getWrappedApplication()
				.plugin(JPAPlugin.class);
		entityManager = jpaPlugin.get().em("default");
		JPA.bindForCurrentThread(entityManager);

		// Get admin (admin is automatically created during initialisation)
		admin = UserModel.findByEmail(UserService.ADMIN_EMAIL);
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
		File studyZip = new File("test/basic_example_study.zip");
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
		commitStudy(studyClone);
		return studyClone;
	}

	protected void commitStudy(StudyModel study) {
		entityManager.getTransaction().begin();
		PersistanceUtils.addStudy(study, admin);
		entityManager.getTransaction().commit();
	}

	protected synchronized void removeStudy(StudyModel study)
			throws IOException {
		IOUtils.removeStudyAssetsDir(study.getDirName());
		entityManager.getTransaction().begin();
		PersistanceUtils.removeStudy(study);
		entityManager.getTransaction().commit();
	}
	
	protected void removeMember(StudyModel studyClone, UserModel member) {
		JPA.em().getTransaction().begin();
		StudyModel.findById(studyClone.getId()).removeMember(member);
		JPA.em().getTransaction().commit();
	}

}
