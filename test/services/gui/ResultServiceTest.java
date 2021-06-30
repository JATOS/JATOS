package services.gui;

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
import models.common.workers.Worker;
import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.fest.assertions.Assertions.assertThat;

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
        testHelper.removeAllStudyLogs();
    }

    @Test
    public void simpleCheck() {
        int a = 1 + 1;
        assertThat(a).isEqualTo(2);
    }

    private void checkForProperResultIdList(List<Long> resultIdList) {
        assertThat(resultIdList.size() == 3);
        assertThat(resultIdList.contains(1L));
        assertThat(resultIdList.contains(2L));
        assertThat(resultIdList.contains(3L));
    }

    @Test
    public void checkCheckComponentResults() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        List<Long> ids = resultTestHelper.createTwoComponentResults(study.getId());

        jpaApi.withTransaction(() -> {
            try {
                List<ComponentResult> componentResultList = resultService.getComponentResults(ids);

                // Must not throw an exception
                User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
                checker.checkComponentResults(componentResultList, admin, true);
            } catch (NotFoundException | ForbiddenException | BadRequestException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void checkCheckComponentResultsWrongUser() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        List<Long> ids = resultTestHelper.createTwoComponentResults(study.getId());

        // Check results with wrong user
        jpaApi.withTransaction(() -> {
            User testUser = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla", "bla");
            try {
                List<ComponentResult> componentResultList = resultService.getComponentResults(ids);
                checker.checkComponentResults(componentResultList, testUser, true);
            } catch (ForbiddenException e) {
                assertThat(e.getMessage()).isEqualTo(MessagesStrings
                        .studyNotUser(testUser.getName(), testUser.getUsername(), study.getId(), study.getTitle()));
            } catch (BadRequestException | NotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    @Test
    public void checkCheckComponentResultsLocked() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        List<Long> ids = resultTestHelper.createTwoComponentResults(study.getId());

        jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            List<ComponentResult> componentResultList;
            try {
                componentResultList = resultService.getComponentResults(ids);

                // Lock study
                componentResultList.get(0).getComponent().getStudy().setLocked(true);
            } catch (NotFoundException e) {
                throw new RuntimeException(e);
            }

            // Must not throw an exception since we tell it not to check for locked study
            try {
                checker.checkComponentResults(componentResultList, admin, false);
            } catch (ForbiddenException | BadRequestException e) {
                throw new RuntimeException(e);
            }

            // Must throw an exception since we told it to check for locked study
            try {
                checker.checkComponentResults(componentResultList, admin, true);
            } catch (ForbiddenException e) {
                assertThat(e.getMessage()).isEqualTo(MessagesStrings.studyLocked(study.getId(), study.getTitle()));
            } catch (BadRequestException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void checkCheckStudyResults() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        List<Long> ids = resultTestHelper.createTwoStudyResults(study.getId());

        jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            try {
                List<StudyResult> studyResultList = resultService.getStudyResults(ids);

                // Must not throw an exception
                checker.checkStudyResults(studyResultList, admin, true);
            } catch (NotFoundException | BadRequestException | ForbiddenException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void checkCheckStudyResultsLocked() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        List<Long> ids = resultTestHelper.createTwoStudyResults(study.getId());

        jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            List<StudyResult> studyResultList;
            try {
                studyResultList = resultService.getStudyResults(ids);
            } catch (NotFoundException e) {
                throw new RuntimeException(e);
            }

            // Lock study
            studyResultList.get(0).getStudy().setLocked(true);

            // Must not throw an exception since we tell it not to check for locked study
            try {
                checker.checkStudyResults(studyResultList, admin, false);
            } catch (ForbiddenException | BadRequestException e) {
                throw new RuntimeException(e);
            }

            // Must throw an exception since we told it to check for locked study
            try {
                checker.checkStudyResults(studyResultList, admin, true);
            } catch (ForbiddenException e) {
                assertThat(e.getMessage()).isEqualTo(MessagesStrings.studyLocked(study.getId(), study.getTitle()));
            } catch (BadRequestException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void checkGetComponentResults() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        List<Long> ids = resultTestHelper.createTwoComponentResults(study.getId());

        // Check that we can get ComponentResults
        jpaApi.withTransaction(() -> {
            try {
                List<ComponentResult> componentResultList = resultService.getComponentResults(ids);
                assertThat(componentResultList.size()).isEqualTo(2);
            } catch (NotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void checkGetComponentResultsWrongId() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        List<Long> ids = resultTestHelper.createTwoComponentResults(study.getId());

        // If one of the IDs don't exist it throws an exception
        jpaApi.withTransaction(() -> {
            try {
                ids.add(1111L);
                resultService.getComponentResults(ids);
                Fail.fail();
            } catch (NotFoundException e) {
                assertThat(e.getMessage()).isEqualTo(MessagesStrings.componentResultNotExist(1111L));
            }
        });
    }

    @Test
    public void checkGetComponentResultsNotExist() {
        testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Check that a NotFoundException is thrown if ComponentResults with ID 1, 2 don't exist
        jpaApi.withTransaction(() -> {
            try {
                List<Long> ids = new ArrayList<>();
                ids.add(1L);
                ids.add(2L);
                resultService.getComponentResults(ids);
                Fail.fail();
            } catch (NotFoundException e) {
                assertThat(e.getMessage()).isEqualTo(MessagesStrings.componentResultNotExist(1L));
            }
        });
    }

    @Test
    public void checkGetStudyResults() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        List<Long> ids = resultTestHelper.createTwoStudyResults(study.getId());

        jpaApi.withTransaction(() -> {
            try {
                List<StudyResult> studyResultList;
                studyResultList = resultService.getStudyResults(ids);
                assertThat(studyResultList.size()).isEqualTo(2);
            } catch (NotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void checkGetStudyResultsNotExist() {
        testHelper.createAndPersistExampleStudyForAdmin(injector);

        // If no results were added an NotFoundException should be thrown
        jpaApi.withTransaction(() -> {
            try {
                List<Long> ids = new ArrayList<>();
                ids.add(1L);
                ids.add(2L);
                resultService.getStudyResults(ids);
                Fail.fail();
            } catch (NotFoundException e) {
                assertThat(e.getMessage()).isEqualTo(MessagesStrings.studyResultNotExist(1L));
            }
        });
    }

    @Test
    public void checkGetAllowedStudyResultList() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        List<Long> ids = resultTestHelper.createTwoStudyResults(study.getId());

        // Leave the StudyResult but remove admin from the users of the corresponding studies
        jpaApi.withTransaction(() -> {
            try {
                User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
                List<StudyResult> studyResultList = resultService.getStudyResults(ids);
                studyResultList.forEach(studyResult -> studyResult.getStudy().removeUser(admin));
            } catch (NotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        // Must be empty
        jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            List<StudyResult> studyResultList = getAllowedStudyResultList(admin, admin.getWorker());
            assertThat(studyResultList.size()).isEqualTo(0);
        });
    }

    @Test
    public void checkGetAllowedStudyResultListEmpty() {
        testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Don't add any results
        jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            List<StudyResult> studyResultList = getAllowedStudyResultList(admin, admin.getWorker());
            assertThat(studyResultList).isEmpty();
        });
    }

    @Test
    public void checkGetAllowedStudyResultListWrongUser() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        resultTestHelper.createTwoStudyResults(study.getId());

        // Use wrong user to retrieve results
        jpaApi.withTransaction(() -> {
            User testUser = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla", "bla");
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            List<StudyResult> studyResultList = getAllowedStudyResultList(admin, testUser.getWorker());
            assertThat(studyResultList).isEmpty();
        });

        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    /**
     * Generate the list of StudyResults that belong to the given Worker and that the given user is allowed to see. A
     * user is allowed if the study that the StudyResult belongs too has this user.
     */
    private List<StudyResult> getAllowedStudyResultList(User user, Worker worker) {
        // Check for studyResult != null should not be necessary but it lead to an NPE at least once
        return worker.getStudyResultList().stream().filter(
                studyResult -> studyResult != null && studyResult.getStudy().hasUser(user)).collect(
                Collectors.toList());
    }

}
