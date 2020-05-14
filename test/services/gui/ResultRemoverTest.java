package services.gui;

import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
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
import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

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
        testHelper.removeAllStudyLogs();
    }

    @Test
    public void simpleCheck() {
        int a = 1 + 1;
        assertThat(a).isEqualTo(2);
    }

    @Test
    public void checkRemoveComponentResults() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        List<Long> ids = resultTestHelper.createTwoComponentResults(study.getId());

        // Now remove both ComponentResults
        jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            try {
                resultRemover.removeComponentResults(ids, admin);
            } catch (BadRequestException | NotFoundException | ForbiddenException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Check that the results are removed
        jpaApi.withTransaction(() -> {
            try {
                resultService.getComponentResults(ids);
                Fail.fail();
            } catch (NotFoundException e) {
                assertThat(e.getMessage()).isEqualTo(MessagesStrings.componentResultNotExist(ids.get(0)));
            }
        });
    }

    @Test
    public void checkRemoveComponentResultsNotFound() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        List<Long> ids = resultTestHelper.createTwoComponentResults(study.getId());

        // Now try to remove the results but one of the result IDs doesn't exist
        jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            ids.add(1111L);
            try {
                resultRemover.removeComponentResults(ids, admin);
                Fail.fail();
            } catch (NotFoundException e) {
                assertThat(e.getMessage()).isEqualTo(MessagesStrings.componentResultNotExist(1111L));
            } catch (ForbiddenException | BadRequestException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Check that NO result is removed - not even the two existing ones
        jpaApi.withTransaction(() -> {
            try {
                ids.remove(1111L);
                List<ComponentResult> componentResultList = resultService.getComponentResults(ids);
                assertThat(componentResultList.size()).isEqualTo(2);
            } catch (NotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void checkRemoveStudyResults() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        List<Long> ids = resultTestHelper.createTwoStudyResults(study.getId());

        // Now remove both StudyResults
        jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            try {
                resultRemover.removeStudyResults(ids, admin);
            } catch (BadRequestException | NotFoundException | ForbiddenException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Check that both are removed
        jpaApi.withTransaction(() -> {
            List<StudyResult> studyResultList = studyResultDao.findAllByStudy(study);
            assertThat(studyResultList.size()).isEqualTo(0);
        });
    }

}
