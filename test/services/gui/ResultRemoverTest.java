package services.gui;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import org.fest.assertions.Fail;
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

/**
 * Tests ResultRemover
 * 
 * @author Kristian Lange
 */
public class ResultRemoverTest {

	private Injector injector;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private ResultTestHelper resultTestHelper;

	@Inject
	private ResultRemover resultRemover;

	@Inject
	private ResultService resultService;

	@Inject
	private UserDao userDao;

	@Inject
	private StudyDao studyDao;

	@Inject
	private StudyResultDao studyResultDao;

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
	public void checkRemoveComponentResults() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String ids = resultTestHelper.createTwoComponentResults(study.getId());

		// Now remove both ComponentResults
		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			try {
				resultRemover.removeComponentResults(ids, admin);
			} catch (BadRequestException | NotFoundException
					| ForbiddenException e) {
				throw new RuntimeException(e);
			}
		});

		// Check that the results are removed
		jpaApi.withTransaction(() -> {
			try {
				List<Long> idList = resultService.extractResultIds("1, 2");
				resultService.getComponentResults(idList);
				Fail.fail();
			} catch (NotFoundException | BadRequestException e) {
				assertThat(e.getMessage())
						.isEqualTo(MessagesStrings.componentResultNotExist(1L));
			}
		});
	}

	@Test
	public void checkRemoveComponentResultsNotFound()
			throws IOException, BadRequestException, ForbiddenException,
			NotFoundException, ForbiddenReloadException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String ids = resultTestHelper.createTwoComponentResults(study.getId());

		// Now try to remove the results but one of the result IDs doesn't exist
		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			long notExistingId = 1111L;
			try {
				resultRemover.removeComponentResults(ids + ", " + notExistingId,
						admin);
				Fail.fail();
			} catch (NotFoundException e) {
				assertThat(e.getMessage()).isEqualTo(
						MessagesStrings.componentResultNotExist(notExistingId));
			} catch (ForbiddenException | BadRequestException e) {
				throw new RuntimeException(e);
			}
		});

		// Check that NO result is removed - not even the two existing ones
		jpaApi.withTransaction(() -> {
			try {
				List<Long> idList = resultService.extractResultIds(ids);
				List<ComponentResult> componentResultList = resultService
						.getComponentResults(idList);
				assertThat(componentResultList.size()).isEqualTo(2);
			} catch (BadRequestException | NotFoundException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	public void checkRemoveStudyResults() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String ids = resultTestHelper.createTwoStudyResults(study.getId());

		// Now remove both StudyResults
		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			try {
				resultRemover.removeStudyResults(ids, admin);
			} catch (BadRequestException | NotFoundException
					| ForbiddenException e) {
				throw new RuntimeException(e);
			}
		});

		// Check that both are removed
		jpaApi.withTransaction(() -> {
			List<StudyResult> studyResultList = studyResultDao
					.findAllByStudy(study);
			assertThat(studyResultList.size()).isEqualTo(0);
		});
	}

	@Test
	public void checkRemoveAllStudyResults() throws IOException,
			ForbiddenException, BadRequestException, ForbiddenReloadException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		// Create some StudyResults
		resultTestHelper.createTwoStudyResults(study.getId());

		// Remove all StudyResults
		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			try {
				resultRemover.removeAllStudyResults(study, admin);
			} catch (BadRequestException | ForbiddenException e) {
				throw new RuntimeException(e);
			}
		});

		// Check that we have no more results
		jpaApi.withTransaction(() -> {
			List<StudyResult> studyResultList = studyResultDao
					.findAllByStudy(study);
			assertThat(studyResultList.size()).isEqualTo(0);
		});
	}

	@Test
	public void checkRemoveAllStudyResultsWrongUser()
			throws IOException, BadRequestException, ForbiddenReloadException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		// Create some StudyResults
		resultTestHelper.createTwoStudyResults(study.getId());

		// And now try to remove them with the wrong user
		jpaApi.withTransaction(() -> {
			User testUser = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL,
					"Bla", "bla");
			try {
				resultRemover.removeAllStudyResults(study, testUser);
				Fail.fail();
			} catch (ForbiddenException e) {
				assertThat(e.getMessage()).isEqualTo(MessagesStrings
						.studyNotUser(testUser.getName(), testUser.getEmail(),
								study.getId(), study.getTitle()));
			} catch (BadRequestException e) {
				throw new RuntimeException(e);
			}
		});

		// Check that we still have 2 results
		jpaApi.withTransaction(() -> {
			List<StudyResult> studyResultList = studyResultDao
					.findAllByStudy(study);
			assertThat(studyResultList.size()).isEqualTo(2);
		});

		// Clean-up
		testHelper.removeUser(TestHelper.BLA_EMAIL);
	}

	@Test
	public void checkRemoveAllStudyResultsStudyLocked()
			throws BadRequestException, IOException, ForbiddenReloadException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		resultTestHelper.createTwoStudyResults(study.getId());

		// Lock study
		jpaApi.withTransaction(() -> {
			study.setLocked(true);
			studyDao.update(study);
		});

		// Now try to remove the StudyResults from the locked study
		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			try {
				resultRemover.removeAllStudyResults(study, admin);
				Fail.fail();
			} catch (ForbiddenException e) {
				assertThat(e.getMessage())
						.isEqualTo(MessagesStrings.studyLocked(study.getId()));
			} catch (BadRequestException e) {
				throw new RuntimeException(e);
			}
		});

		// Check that we still have 2 results
		jpaApi.withTransaction(() -> {
			List<StudyResult> studyResultList = studyResultDao
					.findAllByStudy(study);
			assertThat(studyResultList.size()).isEqualTo(2);
		});
	}

}
