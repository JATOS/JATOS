package services.gui;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import exceptions.publix.ForbiddenReloadException;
import general.TestHelper;
import general.common.MessagesStrings;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import services.publix.ResultCreator;
import services.publix.workers.JatosPublixUtils;

/**
 * Tests ResultDataStringGenerator
 * 
 * @author Kristian Lange
 */
public class ResultDataStringGeneratorTests {

	private Injector injector;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private ResultDataExportService resultDataStringGenerator;

	@Inject
	private JatosPublixUtils jatosPublixUtils;

	@Inject
	private ResultCreator resultCreator;

	@Inject
	private StudyDao studyDao;

	@Inject
	private StudyResultDao studyResultDao;

	@Inject
	private UserDao userDao;

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
	 * Test ResultDataStringGenerator.forWorker()
	 */
	@Test
	public void checkForWorker() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String resultData = jpaApi.withTransaction(() -> {
			try {
				createTwoStudyResults(study.getId());
				User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
				return resultDataStringGenerator.forWorker(admin,
						admin.getWorker());
			} catch (ForbiddenException | BadRequestException
					| ForbiddenReloadException e) {
				throw new RuntimeException(e);
			}
		});
		assertThat(resultData)
				.isEqualTo("1. StudyResult, 1. Component, 1. ComponentResult\n"
						+ "1. StudyResult, 1. Component, 2. ComponentResult\n"
						+ "2. StudyResult, 1. Component, 1. ComponentResult\n"
						+ "2. StudyResult, 1. Component, 2. ComponentResult\n"
						+ "2. StudyResult, 2. Component, 1. ComponentResult\n"
						+ "2. StudyResult, 2. Component, 2. ComponentResult");
	}

	/**
	 * Test ResultDataStringGenerator.forStudy()
	 */
	@Test
	public void checkForStudy() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String resultData = jpaApi.withTransaction(() -> {
			try {
				createTwoStudyResults(study.getId());
				User admin = testHelper.getAdmin();
				return resultDataStringGenerator.forStudy(admin, study);
			} catch (ForbiddenException | BadRequestException
					| ForbiddenReloadException e) {
				throw new RuntimeException(e);
			}
		});
		assertThat(resultData)
				.isEqualTo("1. StudyResult, 1. Component, 1. ComponentResult\n"
						+ "1. StudyResult, 1. Component, 2. ComponentResult\n"
						+ "2. StudyResult, 1. Component, 1. ComponentResult\n"
						+ "2. StudyResult, 1. Component, 2. ComponentResult\n"
						+ "2. StudyResult, 2. Component, 1. ComponentResult\n"
						+ "2. StudyResult, 2. Component, 2. ComponentResult");
	}

	/**
	 * Test ResultDataStringGenerator.forComponent()
	 */
	@Test
	public void checkForComponent() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String resultData = jpaApi.withTransaction(() -> {
			try {
				createTwoStudyResults(study.getId());
				User admin = testHelper.getAdmin();
				return resultDataStringGenerator.forComponent(admin,
						study.getFirstComponent());
			} catch (ForbiddenException | BadRequestException
					| ForbiddenReloadException e) {
				throw new RuntimeException(e);
			}
		});
		assertThat(resultData)
				.isEqualTo("1. StudyResult, 1. Component, 1. ComponentResult\n"
						+ "1. StudyResult, 1. Component, 2. ComponentResult\n"
						+ "2. StudyResult, 1. Component, 1. ComponentResult\n"
						+ "2. StudyResult, 1. Component, 2. ComponentResult");
	}

	/**
	 * Test ResultDataStringGenerator.fromListOfComponentResultIds()
	 */
	@Test
	public void checkFromListOfComponentResultIds()
			throws BadRequestException, ForbiddenException, IOException,
			NotFoundException, ForbiddenReloadException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String resultData = jpaApi.withTransaction(() -> {
			try {
				User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
				String componentResultIds;
				componentResultIds = createTwoComponentResultsWithData(
						study.getId());
				return resultDataStringGenerator.fromListOfComponentResultIds(
						componentResultIds, admin);
			} catch (BadRequestException | NotFoundException
					| ForbiddenException | ForbiddenReloadException e) {
				throw new RuntimeException(e);
			}
		});
		assertThat(resultData).isEqualTo(
				"Thats a first component result.\nThats a second component result.");
	}

	/**
	 * Test ResultDataStringGenerator.fromListOfComponentResultIds() without any
	 * result data
	 */
	@Test
	public void checkFromListOfComponentResultIdsEmpty()
			throws BadRequestException, ForbiddenException, IOException,
			ForbiddenReloadException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		jpaApi.withTransaction(() -> {
			try {
				String componentResultIds = createTwoComponentResultsWithoutData(
						study.getId());
				User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
				resultDataStringGenerator.fromListOfComponentResultIds(
						componentResultIds, admin);
			} catch (NotFoundException e) {
				assertThat(e.getMessage())
						.isEqualTo(MessagesStrings.componentResultNotExist(1l));
			} catch (BadRequestException | ForbiddenException
					| ForbiddenReloadException e) {
				throw new RuntimeException(e);
			}
		});
	}

	/**
	 * Test ResultDataStringGenerator.fromListOfStudyResultIds()
	 */
	@Test
	public void checkFromListOfStudyResultIds() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String resultData = jpaApi.withTransaction(() -> {
			try {
				String ids = createTwoStudyResults(study.getId());
				User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
				return resultDataStringGenerator.fromListOfStudyResultIds(ids,
						admin);
			} catch (NotFoundException | BadRequestException
					| ForbiddenException | ForbiddenReloadException e) {
				throw new RuntimeException(e);
			}
		});
		assertThat(resultData)
				.isEqualTo("1. StudyResult, 1. Component, 1. ComponentResult\n"
						+ "1. StudyResult, 1. Component, 2. ComponentResult\n"
						+ "2. StudyResult, 1. Component, 1. ComponentResult\n"
						+ "2. StudyResult, 1. Component, 2. ComponentResult\n"
						+ "2. StudyResult, 2. Component, 1. ComponentResult\n"
						+ "2. StudyResult, 2. Component, 2. ComponentResult");
	}

	/**
	 * Test ResultDataStringGenerator.fromListOfStudyResultIds() without any
	 * results
	 */
	@Test
	public void checkFromListOfStudyResultIdsEmpty() {
		testHelper.createAndPersistExampleStudyForAdmin(injector);

		// Never added any results
		jpaApi.withTransaction(() -> {
			try {
				User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
				resultDataStringGenerator.fromListOfStudyResultIds("1, 2",
						admin);
			} catch (NotFoundException e) {
				assertThat(e.getMessage())
						.isEqualTo(MessagesStrings.studyResultNotExist(1l));
			} catch (BadRequestException | ForbiddenException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private String createTwoComponentResultsWithData(long studyId)
			throws ForbiddenReloadException {
		// Create StudyResult
		Study study = studyDao.findById(studyId);
		User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());

		// Create 2 ComponentResults
		studyResult = studyResultDao.findById(studyResult.getId());
		study = studyResult.getStudy();
		ComponentResult componentResult1 = jatosPublixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		componentResult1.setData("Thats a first component result.");
		ComponentResult componentResult2 = jatosPublixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		componentResult2.setData("Thats a second component result.");
		return componentResult1.getId() + ", " + componentResult2.getId();
	}

	private String createTwoComponentResultsWithoutData(long studyId)
			throws ForbiddenReloadException {
		// Create StudyResult
		Study study = studyDao.findById(studyId);
		User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());

		// Create 2 ComponentResults without data
		studyResult = studyResultDao.findById(studyResult.getId());
		study = studyResult.getStudy();
		ComponentResult componentResult1 = jatosPublixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		ComponentResult componentResult2 = jatosPublixUtils
				.startComponent(study.getFirstComponent(), studyResult);
		return componentResult1.getId() + ", " + componentResult2.getId();
	}

	private String createTwoStudyResults(long studyId)
			throws ForbiddenReloadException {
		Study study = studyDao.findById(studyId);
		User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);

		// Create first StudyResult with two ComponentResults for the first
		// Component
		StudyResult studyResult1 = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		ComponentResult componentResult11 = jatosPublixUtils
				.startComponent(study.getFirstComponent(), studyResult1);
		componentResult11
				.setData("1. StudyResult, 1. Component, 1. ComponentResult");
		ComponentResult componentResult12 = jatosPublixUtils
				.startComponent(study.getFirstComponent(), studyResult1);
		componentResult12
				.setData("1. StudyResult, 1. Component, 2. ComponentResult");

		// Create second StudyResult with four ComponentResults (two each for
		// the first two Components)
		StudyResult studyResult2 = resultCreator.createStudyResult(study,
				study.getBatchList().get(0), admin.getWorker());
		ComponentResult componentResult211 = jatosPublixUtils
				.startComponent(study.getFirstComponent(), studyResult2);
		componentResult211
				.setData("2. StudyResult, 1. Component, 1. ComponentResult");
		ComponentResult componentResult212 = jatosPublixUtils
				.startComponent(study.getFirstComponent(), studyResult2);
		componentResult212
				.setData("2. StudyResult, 1. Component, 2. ComponentResult");
		ComponentResult componentResult221 = jatosPublixUtils
				.startComponent(study.getComponent(2), studyResult2);
		componentResult221
				.setData("2. StudyResult, 2. Component, 1. ComponentResult");
		ComponentResult componentResult222 = jatosPublixUtils
				.startComponent(study.getComponent(2), studyResult2);
		componentResult222
				.setData("2. StudyResult, 2. Component, 2. ComponentResult");

		return studyResult1.getId() + ", " + studyResult2.getId();
	}

}
