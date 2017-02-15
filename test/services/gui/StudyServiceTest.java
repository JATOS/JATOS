package services.gui;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.inject.Inject;

import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.inject.Guice;
import com.google.inject.Injector;

import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.gui.BadRequestException;
import general.TestHelper;
import general.common.MessagesStrings;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.gui.StudyProperties;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import utils.common.IOUtils;

/**
 * Tests StudyService
 * 
 * @author Kristian Lange
 */
public class StudyServiceTest {

	private Injector injector;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private UserDao userDao;

	@Inject
	private StudyDao studyDao;

	@Inject
	private StudyService studyService;

	@Inject
	private IOUtils ioUtils;

	@Rule
	public ExpectedException exception = ExpectedException.none();

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

	private Study cloneAndPersistStudy(Study studyToBeCloned) {
		return jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			try {
				Study studyClone = studyService.clone(studyToBeCloned);
				studyService.createAndPersistStudy(admin, studyClone);
				return studyClone;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	@Test
	public void checkCloneStudy() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Study clone = cloneAndPersistStudy(study);

		// Equal
		assertThat(clone.getComponentList().size())
				.isEqualTo(study.getComponentList().size());
		assertThat(clone.getFirstComponent().getTitle())
				.isEqualTo(study.getFirstComponent().getTitle());
		assertThat(clone.getLastComponent().getTitle())
				.isEqualTo(study.getLastComponent().getTitle());
		assertThat(clone.getDate()).isEqualTo(study.getDate());
		assertThat(clone.getDescription()).isEqualTo(study.getDescription());
		assertThat(clone.getComments()).isEqualTo(study.getComments());
		assertThat(clone.getJsonData()).isEqualTo(study.getJsonData());
		assertThat(clone.getUserList()).containsOnly(testHelper.getAdmin());
		assertThat(clone.getTitle()).isEqualTo(study.getTitle() + " (clone)");

		// Not equal
		assertThat(clone.isLocked()).isFalse();
		assertThat(clone.getId()).isNotEqualTo(study.getId());
		assertThat(clone.getId()).isPositive();
		assertThat(clone.getDirName()).isEqualTo(study.getDirName() + "_clone");
		assertThat(clone.getUuid()).isNotEqualTo(study.getUuid());
		assertThat(clone.getUuid()).isNotEmpty();

		assertThat(ioUtils.checkStudyAssetsDirExists(clone.getDirName()))
				.isTrue();
	}

	@Test
	public void checkUpdateStudy() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		StudyProperties updatedProps = new StudyProperties();
		updatedProps.setDescription("Changed description");
		updatedProps.setComments("Changed comments");
		updatedProps.setJsonData("{}");
		updatedProps.setTitle("Changed Title");
		updatedProps.setUuid("changed uuid");
		long studyId = study.getId();

		studyService.bindToStudyWithoutDirName(study, updatedProps);

		// Changed
		assertThat(study.getTitle()).isEqualTo(updatedProps.getTitle());
		assertThat(study.getDescription())
				.isEqualTo(updatedProps.getDescription());
		assertThat(study.getComments()).isEqualTo(updatedProps.getComments());
		assertThat(study.getJsonData()).isEqualTo(updatedProps.getJsonData());

		// Unchanged
		assertThat(study.getComponentList().size()).isEqualTo(7);
		assertThat(study.getComponent(1).getTitle())
				.isEqualTo("Show JSON input ");
		assertThat(study.getLastComponent().getTitle())
				.isEqualTo("Quit button");
		assertThat(study.getId()).isEqualTo(studyId);
		assertThat(study.getUserList()).contains(testHelper.getAdmin());
		assertThat(study.getUuid())
				.isEqualTo("5c85bd82-0258-45c6-934a-97ecc1ad6617");
	}

	@Test
	public void checkExchangeUsers() {
		testHelper.mockContext();

		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		User userBla = testHelper.createAndPersistUser("bla@bla.com", "Bla",
				"bla");
		testHelper.createAndPersistUser("blu@blu.com", "Blu", "blu");

		// Exchange users of the study with admin and userBla
		jpaApi.withTransaction(() -> {
			try {
				String[] userList = { UserService.ADMIN_EMAIL,
						userBla.getEmail() };
				studyService.exchangeUsers(study, userList);
			} catch (BadRequestException e) {
				Fail.fail();
			}
		});

		// Check that study's users are admin and userBla
		jpaApi.withTransaction(() -> {
			Study s = studyDao.findByUuid(study.getUuid());
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			assertThat(s.getUserList()).containsOnly(userBla, admin);
		});

		// Exchange users with empty user list should fail
		jpaApi.withTransaction(() -> {
			try {
				String[] userList = {};
				studyService.exchangeUsers(study, userList);
				Fail.fail();
			} catch (BadRequestException e) {
				assertThat(e.getMessage())
						.isEqualTo(MessagesStrings.STUDY_AT_LEAST_ONE_USER);
			}
		});

		// Exchange users with non existent user should fail
		jpaApi.withTransaction(() -> {
			try {
				String[] userList = { "not_exist", "admin" };
				studyService.exchangeUsers(study, userList);
				Fail.fail();
			} catch (BadRequestException e) {
				assertThat(e.getMessage())
						.isEqualTo(MessagesStrings.userNotExist("not_exist"));
			}
		});
	}

	@Test
	public void checkChangeComponentPosition() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		// Change position of component from first to third
		checkChangeToPosition(1, 3, study.getId());

		// And back to first
		checkChangeToPosition(3, 1, study.getId());

		// First component to first position -> still first
		checkChangeToPosition(1, 1, study.getId());

		// Last component to last position -> still last
		int lastPostion = study.getComponentPosition(study.getLastComponent());
		checkChangeToPosition(lastPostion, lastPostion, study.getId());

		// NumberFormatException
		try {
			studyService.changeComponentPosition("bla", study,
					study.getFirstComponent());
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.COULDNT_CHANGE_POSITION_OF_COMPONENT);
		}

		// IndexOutOfBoundsException
		jpaApi.withTransaction(() -> {
			Study s = studyDao.findById(study.getId());
			try {
				studyService.changeComponentPosition("100", s,
						s.getFirstComponent());
				Fail.fail();
			} catch (BadRequestException e) {
				assertThat(e.getMessage()).isEqualTo(MessagesStrings
						.studyReorderUnknownPosition("100", s.getId()));
			}
		});
	}

	@Test
	public void checkRenameStudyAssetsDir() {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		String oldDirName = study.getDirName();

		jpaApi.withTransaction(() -> {
			try {
				studyService.renameStudyAssetsDir(study, "changed_dirname");
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});

		jpaApi.withTransaction(() -> {
			Study s = studyDao.findById(study.getId());
			assertThat(s.getDirName()).isEqualTo("changed_dirname");
			assertThat(ioUtils.checkStudyAssetsDirExists("changed_dirname"))
					.isTrue();
			assertThat(ioUtils.checkStudyAssetsDirExists(oldDirName)).isFalse();
		});
	}

	private void checkChangeToPosition(int fromPosition, int toPosition,
			long studyId) {
		jpaApi.withTransaction(() -> {
			Study s = studyDao.findById(studyId);
			Component c = s.getComponent(fromPosition);
			try {
				studyService.changeComponentPosition("" + toPosition, s, c);
			} catch (BadRequestException e) {
				Fail.fail();
			}
			assertThat(s.getComponent(toPosition)).isEqualTo(c);
		});
	}

}
