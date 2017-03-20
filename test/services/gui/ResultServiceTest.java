package services.gui;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.inject.Inject;

import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import daos.common.UserDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
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

/**
 * Tests ResultService
 * 
 * @author Kristian Lange
 */
public class ResultServiceTest {

	private Injector injector;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private ResultTestHelper resultTestHelper;

	@Inject
	private ResultService resultService;

	@Inject
	private Checker checker;

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

	@Test
	public void checkExtractResultIds() {
		List<Long> resultIdList = null;

		// Valid ID string
		try {
			resultIdList = resultService.extractResultIds("1,2,3");
		} catch (BadRequestException e) {
			Fail.fail();
		}
		checkForProperResultIdList(resultIdList);

		// Still valid, but with weird whitespaces and empty fields
		try {
			resultIdList = resultService
					.extractResultIds(" , ,, 1 ,2    ,  3    , ");
		} catch (BadRequestException e) {
			Fail.fail();
		}
		checkForProperResultIdList(resultIdList);

		// Not valid due to letter instead of number
		try {
			resultIdList = resultService.extractResultIds("1,b,3");
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.resultIdMalformed("b"));
		}

		// Not valid due to empty
		try {
			resultIdList = resultService.extractResultIds("");
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.NO_RESULTS_SELECTED);
		}
	}

	private void checkForProperResultIdList(List<Long> resultIdList) {
		assertThat(resultIdList.size() == 3);
		assertThat(resultIdList.contains(1l));
		assertThat(resultIdList.contains(2l));
		assertThat(resultIdList.contains(3l));
	}

	@Test
	public void checkCheckComponentResults() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String ids = resultTestHelper.createTwoComponentResults(study.getId());

		jpaApi.withTransaction(() -> {
			try {
				List<Long> idList = resultService.extractResultIds(ids);
				List<ComponentResult> componentResultList = resultService
						.getComponentResults(idList);

				// Must not throw an exception
				User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
				checker.checkComponentResults(componentResultList, admin, true);
			} catch (NotFoundException | ForbiddenException
					| BadRequestException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	public void checkCheckComponentResultsWrongUser() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String ids = resultTestHelper.createTwoComponentResults(study.getId());

		// Check results with wrong user
		jpaApi.withTransaction(() -> {
			User testUser = testHelper.createAndPersistUser("bla@bla.com",
					"Bla", "bla");
			try {
				List<Long> idList = resultService.extractResultIds(ids);
				List<ComponentResult> componentResultList = resultService
						.getComponentResults(idList);
				checker.checkComponentResults(componentResultList, testUser,
						true);
			} catch (ForbiddenException e) {
				assertThat(e.getMessage()).isEqualTo(MessagesStrings
						.studyNotUser(testUser.getName(), testUser.getEmail(),
								study.getId(), study.getTitle()));
			} catch (BadRequestException | NotFoundException e) {
				throw new RuntimeException(e);
			}
		});

		testHelper.removeUser("bla@bla.com");
	}

	@Test
	public void checkCheckComponentResultsLocked() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String ids = resultTestHelper.createTwoComponentResults(study.getId());

		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			List<ComponentResult> componentResultList;
			try {
				List<Long> idList = resultService.extractResultIds(ids);
				componentResultList = resultService.getComponentResults(idList);

				// Lock study
				componentResultList.get(0).getComponent().getStudy()
						.setLocked(true);
			} catch (BadRequestException | NotFoundException e) {
				throw new RuntimeException(e);
			}

			// Must not throw an exception since we tell it not to check for
			// locked study
			try {
				checker.checkComponentResults(componentResultList, admin,
						false);
			} catch (ForbiddenException | BadRequestException e) {
				throw new RuntimeException(e);
			}

			// Must throw an exception since we told it to check for locked
			// study
			try {
				checker.checkComponentResults(componentResultList, admin, true);
			} catch (ForbiddenException e) {
				assertThat(e.getMessage())
						.isEqualTo(MessagesStrings.studyLocked(study.getId()));
			} catch (BadRequestException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	public void checkCheckStudyResults()
			throws NoSuchAlgorithmException, IOException, BadRequestException,
			NotFoundException, ForbiddenException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String ids = resultTestHelper.createTwoStudyResults(study.getId());

		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			try {
				List<Long> idList = resultService.extractResultIds(ids);
				List<StudyResult> studyResultList = resultService
						.getStudyResults(idList);

				// Must not throw an exception
				checker.checkStudyResults(studyResultList, admin, true);
			} catch (NotFoundException | BadRequestException
					| ForbiddenException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	public void checkCheckStudyResultsLocked()
			throws NoSuchAlgorithmException, IOException, BadRequestException,
			NotFoundException, ForbiddenException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String ids = resultTestHelper.createTwoStudyResults(study.getId());

		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			List<StudyResult> studyResultList;
			try {
				List<Long> idList = resultService.extractResultIds(ids);
				studyResultList = resultService.getStudyResults(idList);
			} catch (BadRequestException | NotFoundException e) {
				throw new RuntimeException(e);
			}

			// Lock study
			studyResultList.get(0).getStudy().setLocked(true);

			// Must not throw an exception since we tell it not to check for
			// locked study
			try {
				checker.checkStudyResults(studyResultList, admin, false);
			} catch (ForbiddenException | BadRequestException e) {
				throw new RuntimeException(e);
			}

			// Must throw an exception since we told it to check for locked
			// study
			try {
				checker.checkStudyResults(studyResultList, admin, true);
			} catch (ForbiddenException e) {
				assertThat(e.getMessage())
						.isEqualTo(MessagesStrings.studyLocked(study.getId()));
			} catch (BadRequestException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	public void checkGetComponentResults() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String ids = resultTestHelper.createTwoComponentResults(study.getId());

		// Check that we can get ComponentResults
		jpaApi.withTransaction(() -> {
			try {
				List<Long> idList = resultService.extractResultIds(ids);
				List<ComponentResult> componentResultList = null;
				componentResultList = resultService.getComponentResults(idList);
				assertThat(componentResultList.size()).isEqualTo(2);
			} catch (BadRequestException | NotFoundException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	public void checkGetComponentResultsWrongId() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String ids = resultTestHelper.createTwoComponentResults(study.getId());

		// If one of the IDs don't exist it throws an exception
		jpaApi.withTransaction(() -> {
			long nonExistingId = 1111l;
			try {
				List<Long> idList = resultService
						.extractResultIds(ids + ", " + nonExistingId);
				resultService.getComponentResults(idList);
				Fail.fail();
			} catch (NotFoundException e) {
				assertThat(e.getMessage()).isEqualTo(
						MessagesStrings.componentResultNotExist(nonExistingId));
			} catch (BadRequestException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	public void checkGetComponentResultsNotExist() {
		testHelper.createAndPersistExampleStudyForAdmin(injector);

		// Check that a NotFoundException is thrown if ComponentResults with ID
		// 1, 2 don't exist
		jpaApi.withTransaction(() -> {
			try {
				List<Long> idList = resultService.extractResultIds("1, 2");
				resultService.getComponentResults(idList);
				Fail.fail();
			} catch (NotFoundException e) {
				assertThat(e.getMessage())
						.isEqualTo(MessagesStrings.componentResultNotExist(1l));
			} catch (BadRequestException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	public void checkGetStudyResults() throws IOException,
			NoSuchAlgorithmException, BadRequestException, NotFoundException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String ids = resultTestHelper.createTwoStudyResults(study.getId());

		jpaApi.withTransaction(() -> {
			try {
				List<Long> idList = resultService.extractResultIds(ids);
				List<StudyResult> studyResultList;
				studyResultList = resultService.getStudyResults(idList);
				assertThat(studyResultList.size()).isEqualTo(2);
			} catch (NotFoundException | BadRequestException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	public void checkGetStudyResultsNotExist()
			throws NoSuchAlgorithmException, IOException, BadRequestException {
		testHelper.createAndPersistExampleStudyForAdmin(injector);

		// If no results were added an NotFoundException should be thrown
		jpaApi.withTransaction(() -> {
			try {
				List<Long> idList = resultService.extractResultIds("1, 2");
				resultService.getStudyResults(idList);
				Fail.fail();
			} catch (NotFoundException e) {
				assertThat(e.getMessage())
						.isEqualTo(MessagesStrings.studyResultNotExist(1l));
			} catch (BadRequestException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	public void checkGetAllowedStudyResultList()
			throws NoSuchAlgorithmException, IOException, BadRequestException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String ids = resultTestHelper.createTwoStudyResults(study.getId());

		// Leave the StudyResult but remove admin from the users of the
		// corresponding studies
		jpaApi.withTransaction(() -> {
			try {
				User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
				List<Long> idList = resultService.extractResultIds(ids);
				List<StudyResult> studyResultList = resultService
						.getStudyResults(idList);
				studyResultList.forEach(studyResult -> {
					studyResult.getStudy().removeUser(admin);
				});
			} catch (BadRequestException | NotFoundException e) {
				throw new RuntimeException(e);
			}
		});

		// Must be empty
		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			List<StudyResult> studyResultList = resultService
					.getAllowedStudyResultList(admin, admin.getWorker());
			assertThat(studyResultList.size()).isEqualTo(0);
		});
	}

	@Test
	public void checkGetAllowedStudyResultListEmpty()
			throws IOException, NoSuchAlgorithmException {
		testHelper.createAndPersistExampleStudyForAdmin(injector);

		// Don't add any results
		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			List<StudyResult> studyResultList = resultService
					.getAllowedStudyResultList(admin, admin.getWorker());
			assertThat(studyResultList).isEmpty();
		});
	}

	@Test
	public void checkGetAllowedStudyResultListWrongUser()
			throws NoSuchAlgorithmException, IOException, BadRequestException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		resultTestHelper.createTwoStudyResults(study.getId());

		// Use wrong user to retrieve results
		jpaApi.withTransaction(() -> {
			User testUser = testHelper.createAndPersistUser("bla@bla.com",
					"Bla", "bla");
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			List<StudyResult> studyResultList = resultService
					.getAllowedStudyResultList(admin, testUser.getWorker());
			assertThat(studyResultList).isEmpty();
		});

		testHelper.removeUser("bla@bla.com");
	}

}
