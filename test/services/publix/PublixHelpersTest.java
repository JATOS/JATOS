package services.publix;

import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.UserDao;
import exceptions.publix.ForbiddenReloadException;
import general.TestHelper;
import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.User;
import models.common.workers.JatosWorker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import services.gui.UserService;

import javax.inject.Inject;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for class PublixHelpers
 *
 * @author Kristian Lange
 */
public class PublixHelpersTest {

    private Injector injector;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private ResultCreator resultCreator;

    @Inject
    private UserDao userDao;

    // The worker is not important here
    @Inject
    private PublixUtils<JatosWorker> publixUtils;

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

    /**
     * Test PublixUtils.finishedStudyAlready(): check for all different states
     * of a StudyResult
     */
    @Test
    public void checkFinishedStudyAlready() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            StudyResult studyResult = resultCreator
                    .createStudyResult(study, study.getDefaultBatch(), admin.getWorker());

            // Study results in state FINISHED, ABORTED, or FAIL must return true
            studyResult.setStudyState(StudyState.FINISHED);
            assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study)).isTrue();
            studyResult.setStudyState(StudyState.ABORTED);
            assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study)).isTrue();
            studyResult.setStudyState(StudyState.FAIL);
            assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study)).isTrue();

            // Study results in state PRE, STARTED, or DATA_RETRIEVED must return false
            studyResult.setStudyState(StudyState.PRE);
            assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study)).isFalse();
            studyResult.setStudyState(StudyState.STARTED);
            assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study)).isFalse();
            studyResult.setStudyState(StudyState.DATA_RETRIEVED);
            assertThat(PublixHelpers.finishedStudyAlready(admin.getWorker(), study)).isFalse();
        });
    }

    /**
     * Test PublixUtils.didStudyAlready(): normal functioning
     */
    @Test
    public void checkDidStudyAlready() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            assertThat(PublixHelpers.didStudyAlready(admin.getWorker(), study)).isFalse();

            // Create a result for the admin's worker
            resultCreator.createStudyResult(study, study.getDefaultBatch(), admin.getWorker());

            assertThat(PublixHelpers.didStudyAlready(admin.getWorker(), study)).isTrue();
        });
    }

    /**
     * Tests PublixHelpers.studyDone() for the different study result states
     */
    @Test
    public void checkStudyDone() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            StudyResult studyResult = resultCreator.createStudyResult(study,
                    study.getDefaultBatch(), admin.getWorker());

            // FINISHED, ABORTED, FAIL must return true
            studyResult.setStudyState(StudyState.FINISHED);
            assertThat(PublixHelpers.studyDone(studyResult)).isTrue();
            studyResult.setStudyState(StudyState.ABORTED);
            assertThat(PublixHelpers.studyDone(studyResult)).isTrue();
            studyResult.setStudyState(StudyState.FAIL);
            assertThat(PublixHelpers.studyDone(studyResult)).isTrue();

            // DATA_RETRIEVED, STARTED must return false
            studyResult.setStudyState(StudyState.PRE);
            assertThat(PublixHelpers.studyDone(studyResult)).isFalse();
            studyResult.setStudyState(StudyState.STARTED);
            assertThat(PublixHelpers.studyDone(studyResult)).isFalse();
            studyResult.setStudyState(StudyState.DATA_RETRIEVED);
            assertThat(PublixHelpers.studyDone(studyResult)).isFalse();

        });
    }

    /**
     * Tests PublixHelpers.componentDone() for all the different component
     * result states
     */
    @Test
    public void checkComponentDone() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {

            // Create a study result and start a component to get a component result
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            StudyResult studyResult = resultCreator
                    .createStudyResult(study, study.getDefaultBatch(), admin.getWorker());

            ComponentResult componentResult;
            try {
                componentResult =
                        publixUtils.startComponent(study.getFirstComponent().get(), studyResult);
            } catch (ForbiddenReloadException e) {
                throw new RuntimeException(e);
            }

            // A component is done if state FINISHED, ABORTED, FAIL, or RELOADED
            componentResult.setComponentState(ComponentState.FINISHED);
            assertThat(PublixHelpers.componentDone(componentResult)).isTrue();
            componentResult.setComponentState(ComponentState.ABORTED);
            assertThat(PublixHelpers.componentDone(componentResult)).isTrue();
            componentResult.setComponentState(ComponentState.FAIL);
            assertThat(PublixHelpers.componentDone(componentResult)).isTrue();
            componentResult.setComponentState(ComponentState.RELOADED);
            assertThat(PublixHelpers.componentDone(componentResult)).isTrue();

            // Not done if
            componentResult.setComponentState(ComponentState.DATA_RETRIEVED);
            assertThat(PublixHelpers.componentDone(componentResult)).isFalse();
            componentResult.setComponentState(ComponentState.RESULTDATA_POSTED);
            assertThat(PublixHelpers.componentDone(componentResult)).isFalse();
            componentResult.setComponentState(ComponentState.STARTED);
            assertThat(PublixHelpers.componentDone(componentResult)).isFalse();
        });
    }

}
