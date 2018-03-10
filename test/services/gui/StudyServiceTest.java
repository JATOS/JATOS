package services.gui;

import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.BatchDao;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import general.TestHelper;
import general.common.MessagesStrings;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.gui.StudyProperties;
import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import utils.common.IOUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests StudyService class
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
    private BatchDao batchDao;

    @Inject
    private ComponentDao componentDao;

    @Inject
    private StudyService studyService;

    @Inject
    private IOUtils ioUtils;

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
        testHelper.removeAllStudyLogs();
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

    /**
     * StudyService.clone(): clones a study but does not persist. This includes
     * the Components, Batches and asset directory.
     */
    @Test
    public void checkClone() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Study clone = cloneAndPersistStudy(study);

        // Check properties equal in original study and clone
        assertThat(clone.getComponentList().size()).isEqualTo(study.getComponentList().size());
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

        // Check properties that are not equal
        assertThat(clone.isLocked()).isFalse();
        assertThat(clone.getId()).isNotEqualTo(study.getId());
        assertThat(clone.getId()).isPositive();
        assertThat(clone.getDirName()).isEqualTo(study.getDirName() + "_clone");
        assertThat(clone.getUuid()).isNotEqualTo(study.getUuid());
        assertThat(clone.getUuid()).isNotEmpty();

        assertThat(ioUtils.checkStudyAssetsDirExists(clone.getDirName())).isTrue();
    }

    /**
     * StudyService.changeUserMember(): adding or deletion of the users to the
     * members of a study
     */
    @Test
    public void checkChangeUserMember() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla", "bla");
        testHelper.createAndPersistUser("blu@blu.com", "Blu", "blu");

        // Add user Bla but not user Blu
        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                studyService.changeUserMember(s, userBla, true);
            } catch (ForbiddenException e) {
                Fail.fail();
            }
        });

        // Check that study's users are admin and user Bla
        jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            User uBla = userDao.findByEmail(TestHelper.BLA_EMAIL);
            User uBlu = userDao.findByEmail("blu@blu.com");
            assertThat(s.getUserList()).containsOnly(uBla, admin);
            assertThat(admin.getStudyList()).contains(s);
            assertThat(uBla.getStudyList()).contains(s);
            assertThat(uBlu.getStudyList()).excludes(s);
        });

        // Remove user Bla again
        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                studyService.changeUserMember(s, userBla, false);
            } catch (ForbiddenException e) {
                Fail.fail();
            }
        });

        // Check that study's user is only admin user
        jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            User uBla = userDao.findByEmail(TestHelper.BLA_EMAIL);
            User uBlu = userDao.findByEmail("blu@blu.com");
            assertThat(s.getUserList()).containsOnly(admin);
            assertThat(admin.getStudyList()).contains(s);
            assertThat(uBla.getStudyList()).excludes(s);
            assertThat(uBlu.getStudyList()).excludes(s);
        });

        testHelper.removeUser(TestHelper.BLA_EMAIL);
        testHelper.removeUser("blu@blu.com");
    }

    /**
     * StudyService.addAllUserMembers(): adding all users to the members of a study
     * StudyService.removeAllUserMembers(): remove all users from the members of a study
     */
    @Test
    public void checkAddAndRemoveAllUserMember() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla", "bla");
        testHelper.createAndPersistUser("foo@foo.com", "Foo", "foo");
        testHelper.createAndPersistUser("bar@bar.com", "Bar", "bar");

        // Add all users to members of study
        jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            studyService.addAllUserMembers(s);
        });

        // Check that all users are members
        jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            User uBla = userDao.findByEmail(TestHelper.BLA_EMAIL);
            User uFoo = userDao.findByEmail("foo@foo.com");
            User uBar = userDao.findByEmail("bar@bar.com");
            assertThat(s.getUserList()).containsOnly(uBla, uFoo, uBar, admin);
            assertThat(admin.getStudyList()).contains(s);
            assertThat(uBla.getStudyList()).contains(s);
            assertThat(uFoo.getStudyList()).contains(s);
            assertThat(uBar.getStudyList()).contains(s);
        });

        // Remove all users from members of study except logged-in user
        testHelper.defineLoggedInUser(testHelper.getAdmin());
        jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            studyService.removeAllUserMembers(s);
        });

        // Check that only logged-in user (admin) is member
        jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            User uBla = userDao.findByEmail(TestHelper.BLA_EMAIL);
            User uFoo = userDao.findByEmail("foo@foo.com");
            User uBar = userDao.findByEmail("bar@bar.com");
            assertThat(s.getUserList()).containsOnly(admin);
            assertThat(admin.getStudyList()).contains(s);
            assertThat(uBla.getStudyList()).excludes(s);
            assertThat(uFoo.getStudyList()).excludes(s);
            assertThat(uBar.getStudyList()).excludes(s);
        });

        testHelper.removeUser(TestHelper.BLA_EMAIL);
        testHelper.removeUser("foo@foo.com");
        testHelper.removeUser("bar@bar.com");
    }

    /**
     * StudyService.changeUserMember(): adding or deletion of the same user
     * twice shouldn't change the outcome
     */
    @Test
    public void checkChangeUserMemberDouble() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla", "bla");

        // Add user Bla twice: no exception should be thrown
        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                studyService.changeUserMember(s, userBla, true);
            } catch (ForbiddenException e) {
                Fail.fail();
            }
        });
        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                studyService.changeUserMember(s, userBla, true);
            } catch (ForbiddenException e) {
                Fail.fail();
            }
        });
        // Check that study's users are only admin and user Bla
        jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            assertThat(s.getUserList()).containsOnly(userBla, admin);
        });

        // Remove user Bla twice: no exception should be thrown
        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                studyService.changeUserMember(s, userBla, false);
            } catch (ForbiddenException e) {
                Fail.fail();
            }
        });
        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                studyService.changeUserMember(s, userBla, false);
            } catch (ForbiddenException e) {
                Fail.fail();
            }
        });
        // Check that study's users is only admin
        jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            assertThat(s.getUserList()).containsOnly(admin);
        });

        testHelper.removeUser(userBla.getEmail());
    }

    /**
     * StudyService.changeUserMember(): study must have at least one member user
     */
    @Test
    public void checkChangeUserMemberAtLeastOne() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // If one tries to remove the last user of a study an exception is
        // thrown
        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
                studyService.changeUserMember(s, admin, false);
                Fail.fail();
            } catch (ForbiddenException e) {
                // Must throw an ForbiddenException
            }
        });

        // But if the user to be removed isn't member of the study it doesn't
        // lead to an exception
        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla",
                "bla");
        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                studyService.changeUserMember(s, userBla, false);
            } catch (ForbiddenException e) {
                Fail.fail();
            }
        });

        testHelper.removeUser(userBla.getEmail());
    }

    /**
     * StudyService.changeComponentPosition(): change the position of a
     * component within the study (hint: the first position is 1 and not 0)
     */
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

        // NumberFormatException if the position isn't a number
        try {
            studyService.changeComponentPosition("bla", study,
                    study.getFirstComponent());
            Fail.fail();
        } catch (BadRequestException e) {
            assertThat(e.getMessage()).isEqualTo(
                    MessagesStrings.COULDNT_CHANGE_POSITION_OF_COMPONENT);
        }

        // IndexOutOfBoundsException if the position isn't within the study
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

    /**
     * StudyService.bindToStudyWithoutDirName(): Update properties of study with
     * properties of updatedStudy (excluding study's dir name). Doesn't persist.
     */
    @Test
    public void checkBindToStudyWithoutDirName() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        StudyProperties updatedProps = new StudyProperties();
        updatedProps.setDescription("Changed description");
        updatedProps.setComments("Changed comments");
        updatedProps.setJsonData("{}");
        updatedProps.setTitle("Changed Title");
        updatedProps.setUuid("changed uuid");
        long studyId = study.getId();

        studyService.bindToStudyWithoutDirName(study, updatedProps);

        // Check changed properties of the study
        assertThat(study.getTitle()).isEqualTo(updatedProps.getTitle());
        assertThat(study.getDescription())
                .isEqualTo(updatedProps.getDescription());
        assertThat(study.getComments()).isEqualTo(updatedProps.getComments());
        assertThat(study.getJsonData()).isEqualTo(updatedProps.getJsonData());

        // Check the unchanged properties
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

    /**
     * StudyService.renameStudyAssetsDir()
     */
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

    /**
     * StudyService.remove()
     */
    @Test
    public void checkRemove() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                studyService.removeStudyInclAssets(s);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // Check everything was removed
        jpaApi.withTransaction(() -> {
            // Check that the study is removed from the database
            Study s = studyDao.findById(study.getId());
            assertThat(s).isNull();

            // Check that all components are gone
            study.getComponentList().forEach(
                    c -> assertThat(componentDao.findById(c.getId())).isNull());

            // Check all batches are gone
            study.getBatchList().forEach(
                    b -> assertThat(batchDao.findById(b.getId())).isNull());

            // This study is removed from all its member users
            study.getUserList()
                    .forEach(u -> assertThat(
                            userDao.findByEmail(u.getEmail()).hasStudy(s))
                            .isFalse());
        });

        // Check study assets are removed
        assertThat(ioUtils.checkStudyAssetsDirExists(study.getDirName()))
                .isFalse();
    }

}
