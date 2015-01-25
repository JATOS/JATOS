import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.persistence.EntityManager;

import models.StudyModel;
import models.UserModel;
import play.Logger;
import play.db.jpa.JPA;
import play.db.jpa.JPAPlugin;
import play.test.FakeApplication;
import play.test.Helpers;
import scala.Option;
import services.IOUtils;
import services.JsonUtils.UploadUnmarshaller;
import services.PersistanceUtils;
import services.ZipUtil;

import common.Initializer;

import controllers.publix.StudyAssets;

/**
 * Utils class to test controllers. Set up a fake application with it's
 * own database, import a study, etc.
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
		admin = UserModel.findByEmail(Initializer.ADMIN_EMAIL);
	}

	protected void stopApp() throws IOException {
		entityManager.close();
		JPA.bindForCurrentThread(null);
		boolean result = new File(StudyAssets.STUDY_ASSETS_ROOT_PATH).delete();
		if (!result) {
			Logger.warn(CLASS_NAME
					+ ".stopApp: Couldn't remove study assets root directory "
					+ StudyAssets.STUDY_ASSETS_ROOT_PATH
					+ " after finished testing. This should not happen.");
		}
		Helpers.stop(application);
	}

	protected StudyModel importStudyTemplate()
			throws NoSuchAlgorithmException, IOException {
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

		PersistanceUtils.addStudy(importedStudy, admin);
		tempUnzippedStudyDir.delete();
		return importedStudy;
	}

	protected synchronized StudyModel cloneStudy(StudyModel studyToBeCloned)
			throws IOException {
		entityManager.getTransaction().begin();
		StudyModel studyClone = new StudyModel(studyToBeCloned);
		String destDirName;
		destDirName = IOUtils
				.cloneStudyAssetsDirectory(studyClone.getDirName());
		studyClone.setDirName(destDirName);
		PersistanceUtils.addStudy(studyClone, admin);
		entityManager.getTransaction().commit();
		return studyClone;
	}

	protected synchronized void removeStudy(StudyModel study)
			throws IOException {
		IOUtils.removeStudyAssetsDir(study.getDirName());
		entityManager.getTransaction().begin();
		PersistanceUtils.removeStudy(study);
		entityManager.getTransaction().commit();
	}

}
