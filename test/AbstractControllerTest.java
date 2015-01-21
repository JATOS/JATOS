import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.persistence.EntityManager;

import models.StudyModel;
import models.UserModel;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

import play.Logger;
import play.db.jpa.JPA;
import play.db.jpa.JPAPlugin;
import play.test.FakeApplication;
import play.test.Helpers;
import play.test.WithApplication;
import scala.Option;
import services.IOUtils;
import services.JsonUtils.UploadUnmarshaller;
import services.PersistanceUtils;
import services.ZipUtil;
import common.Initializer;
import controllers.publix.StudyAssets;

/**
 * Abstract class to test controllers. We set up a fake application with it's
 * own database. We import an example study template and provide methods to
 * clone it.
 * 
 * @author Kristian Lange
 */
public abstract class AbstractControllerTest extends WithApplication {

	private static final String CLASS_NAME = AbstractControllerTest.class
			.getSimpleName();

	protected static FakeApplication app;
	protected static EntityManager em;
	protected static UserModel admin;
	protected static StudyModel studyTemplate;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@BeforeClass
	public static void startApp() throws Exception {
		app = Helpers.fakeApplication();
		Helpers.start(app);

		Option<JPAPlugin> jpaPlugin = app.getWrappedApplication().plugin(
				JPAPlugin.class);
		em = jpaPlugin.get().em("default");
		JPA.bindForCurrentThread(em);

		// Get admin (admin is automatically created during initialization)
		admin = UserModel.findByEmail(Initializer.ADMIN_EMAIL);

		studyTemplate = importStudyTemplate();
	}

	@Before
	public void setUp() {
	}

	@After
	public void tearDown() throws IOException {
	}

	@AfterClass
	public static void stopApp() throws IOException {
		em.close();
		JPA.bindForCurrentThread(null);
		IOUtils.removeStudyAssetsDir(studyTemplate.getDirName());
		boolean result = new File(StudyAssets.STUDY_ASSETS_ROOT_PATH).delete();
		if (!result) {
			Logger.warn(CLASS_NAME
					+ ".stopApp: Couldn't remove study assets root directory "
					+ StudyAssets.STUDY_ASSETS_ROOT_PATH
					+ " after finished testing. This should not happen.");
		}
		Helpers.stop(app);
	}

	private static StudyModel importStudyTemplate()
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

	protected synchronized StudyModel cloneStudy() throws IOException {
		JPA.em().getTransaction().begin();
		StudyModel studyClone = new StudyModel(studyTemplate);
		String destDirName;
		destDirName = IOUtils
				.cloneStudyAssetsDirectory(studyClone.getDirName());
		studyClone.setDirName(destDirName);
		PersistanceUtils.addStudy(studyClone, admin);
		JPA.em().getTransaction().commit();
		return studyClone;
	}

	protected synchronized void removeStudy(StudyModel study)
			throws IOException {
		IOUtils.removeStudyAssetsDir(study.getDirName());
		JPA.em().getTransaction().begin();
		PersistanceUtils.removeStudy(study);
		JPA.em().getTransaction().commit();
	}

}
