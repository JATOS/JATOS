package services.gui;

import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.publix.ForbiddenReloadException;
import general.TestHelper;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import services.publix.ResultCreator;
import services.publix.workers.JatosPublixUtils;

import javax.inject.Inject;

import static org.fest.assertions.Assertions.assertThat;

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
    private ResultDataExporter resultDataExporter;

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
        testHelper.removeAllStudyLogs();
    }

    @Test
    public void simpleCheck() {
        int a = 1 + 1;
        assertThat(a).isEqualTo(2);
    }

    /**
     * Test ResultDataStringGenerator.getResultDataByWorker()
     */
    @Test
    public void checkForWorker() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        String resultData = jpaApi.withTransaction(() -> {
            try {
                createTwoStudyResults(study.getId());
                User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
                return resultDataExporter.getResultDataByWorker(admin, admin.getWorker());
            } catch (ForbiddenException | BadRequestException | ForbiddenReloadException e) {
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
     * Test ResultDataStringGenerator.getResultDataByStudy()
     */
    @Test
    public void checkForStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        String resultData = jpaApi.withTransaction(() -> {
            try {
                createTwoStudyResults(study.getId());
                User admin = testHelper.getAdmin();
                return resultDataExporter.getResultDataByStudy(admin, study);
            } catch (ForbiddenException | BadRequestException | ForbiddenReloadException e) {
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
                return resultDataExporter
                        .forComponent(admin, study.getFirstComponent().get());
            } catch (ForbiddenException | BadRequestException | ForbiddenReloadException e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(resultData)
                .isEqualTo("1. StudyResult, 1. Component, 1. ComponentResult\n"
                        + "1. StudyResult, 1. Component, 2. ComponentResult\n"
                        + "2. StudyResult, 1. Component, 1. ComponentResult\n"
                        + "2. StudyResult, 1. Component, 2. ComponentResult");
    }

    private String createTwoComponentResultsWithData(long studyId)
            throws ForbiddenReloadException {
        // Create StudyResult
        Study study = studyDao.findById(studyId);
        User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
        StudyResult studyResult =
                resultCreator.createStudyResult(study, study.getDefaultBatch(), admin.getWorker());

        // Create 2 ComponentResults
        studyResult = studyResultDao.findById(studyResult.getId());
        study = studyResult.getStudy();
        ComponentResult componentResult1 = jatosPublixUtils
                .startComponent(study.getFirstComponent().get(), studyResult);
        componentResult1.setData("Thats a first component result.");
        ComponentResult componentResult2 = jatosPublixUtils
                .startComponent(study.getFirstComponent().get(), studyResult);
        componentResult2.setData("Thats a second component result.");
        return componentResult1.getId() + ", " + componentResult2.getId();
    }

    private String createTwoComponentResultsWithoutData(long studyId)
            throws ForbiddenReloadException {
        // Create StudyResult
        Study study = studyDao.findById(studyId);
        User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
        StudyResult studyResult =
                resultCreator.createStudyResult(study, study.getDefaultBatch(), admin.getWorker());
        studyResult.setWorker(admin.getWorker());

        // Create 2 ComponentResults without data
        studyResult = studyResultDao.findById(studyResult.getId());
        study = studyResult.getStudy();
        ComponentResult componentResult1 =
                jatosPublixUtils.startComponent(study.getFirstComponent().get(), studyResult);
        ComponentResult componentResult2 =
                jatosPublixUtils.startComponent(study.getFirstComponent().get(), studyResult);
        return componentResult1.getId() + ", " + componentResult2.getId();
    }

    private String createTwoStudyResults(long studyId) throws ForbiddenReloadException {
        Study study = studyDao.findById(studyId);
        User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);

        // Create first StudyResult with two ComponentResults for the first
        // Component
        StudyResult studyResult1 =
                resultCreator.createStudyResult(study, study.getDefaultBatch(), admin.getWorker());
        ComponentResult componentResult11 = jatosPublixUtils
                .startComponent(study.getFirstComponent().get(), studyResult1);
        componentResult11.setData("1. StudyResult, 1. Component, 1. ComponentResult");
        ComponentResult componentResult12 = jatosPublixUtils
                .startComponent(study.getFirstComponent().get(), studyResult1);
        componentResult12.setData("1. StudyResult, 1. Component, 2. ComponentResult");

        // Create second StudyResult with four ComponentResults (two each for
        // the first two Components)
        StudyResult studyResult2 = resultCreator.createStudyResult(study,
                study.getBatchList().get(0), admin.getWorker());
        ComponentResult componentResult211 = jatosPublixUtils
                .startComponent(study.getFirstComponent().get(), studyResult2);
        componentResult211.setData("2. StudyResult, 1. Component, 1. ComponentResult");
        ComponentResult componentResult212 = jatosPublixUtils
                .startComponent(study.getFirstComponent().get(), studyResult2);
        componentResult212.setData("2. StudyResult, 1. Component, 2. ComponentResult");
        ComponentResult componentResult221 = jatosPublixUtils
                .startComponent(study.getComponent(2), studyResult2);
        componentResult221.setData("2. StudyResult, 2. Component, 1. ComponentResult");
        ComponentResult componentResult222 = jatosPublixUtils
                .startComponent(study.getComponent(2), studyResult2);
        componentResult222.setData("2. StudyResult, 2. Component, 2. ComponentResult");

        return studyResult1.getId() + ", " + studyResult2.getId();
    }

}
