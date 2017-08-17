package services.publix;

import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.*;
import daos.common.worker.WorkerDao;
import exceptions.publix.*;
import general.TestHelper;
import models.common.*;
import models.common.ComponentResult.ComponentState;
import models.common.StudyResult.StudyState;
import models.common.workers.JatosWorker;
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
import play.mvc.Http.Cookie;
import services.gui.BatchService;
import services.gui.StudyService;
import services.gui.UserService;
import services.publix.idcookie.IdCookieCollection;
import services.publix.idcookie.IdCookieModel;
import services.publix.idcookie.IdCookieService;
import services.publix.idcookie.IdCookieTestHelper;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for class PublixUtils
 *
 * @author Kristian Lange
 */
public abstract class PublixUtilsTest<T extends Worker> {

    private Injector injector;

    @Inject
    protected TestHelper testHelper;

    @Inject
    protected JPAApi jpaApi;

    @Inject
    protected PublixUtils<T> publixUtils;

    @Inject
    protected PublixErrorMessages errorMessages;

    @Inject
    protected IdCookieService idCookieService;

    @Inject
    protected IdCookieTestHelper idCookieTestHelper;

    @Inject
    protected ResultCreator resultCreator;

    @Inject
    protected UserDao userDao;

    @Inject
    protected StudyDao studyDao;

    @Inject
    protected ComponentDao componentDao;

    @Inject
    protected ComponentResultDao componentResultDao;

    @Inject
    protected StudyResultDao studyResultDao;

    @Inject
    protected WorkerDao workerDao;

    @Inject
    protected BatchDao batchDao;

    @Inject
    protected StudyService studyService;

    @Inject
    protected BatchService batchService;

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
     * Check normal functioning of PublixUtils.retrieveWorker() - belongs to the
     * abstract class and is tested here (don't confuse this method with
     * PublixUtils.retrieveTypedWorker() which is tested in the concrete
     * classes)
     */
    @Test
    public void checkRetrieveWorker() {
        Worker worker = jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            try {
                return publixUtils.retrieveWorker(admin.getWorker().getId());
            } catch (ForbiddenPublixException e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(worker).isNotNull();
        assertThat(worker).isEqualTo(testHelper.getAdmin().getWorker());
    }

    /**
     * Test PublixUtils.retrieveWorker(): if worker doesn't exist a
     * ForbiddenPublixException should be thrown
     */
    @Test
    public void checkRetrieveWorkerNotExist() {
        jpaApi.withTransaction(() -> {
            try {
                publixUtils.retrieveWorker(2222l);
                Fail.fail();
            } catch (ForbiddenPublixException e) {
                assertThat(e.getMessage())
                        .isEqualTo(PublixErrorMessages.workerNotExist("2222"));
            }
        });
    }

    /**
     * Test PublixUtils.startComponent() normal functioning
     */
    @Test
    public void checkStartComponent() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long studyResultId = createStudyResultAndStartFirstComponent(study);

        // Check that everything went normal
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            assertThat(studyResult.getComponentResultList().size())
                    .isEqualTo(1);
            assertThat(studyResult.getComponentResultList().get(0)
                    .getComponentState()).isEqualTo(ComponentState.STARTED);
            assertThat(
                    studyResult.getComponentResultList().get(0).getStartDate())
                    .isNotNull();
        });
    }

    /**
     * Test PublixUtils.startComponent(): after starting a second component in
     * the same study run, the first component result should be finished
     * automatically
     */
    @Test
    public void checkStartComponentFinishPriorComponentResult() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long studyResultId = createStudyResultAndStartFirstComponent(study);

        // Start a different component than the first one
        startComponentByPosition(2, study, studyResultId);

        // Check
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            ComponentResult componentResult1 = studyResult
                    .getComponentResultList().get(0);
            ComponentResult componentResult2 = studyResult
                    .getComponentResultList().get(1);

            // Check new ComponentResult
            assertThat(componentResult2.getComponentState())
                    .isEqualTo(ComponentState.STARTED);

            // Check that prior ComponentResult was finished properly
            assertThat(componentResult1.getComponentState())
                    .isEqualTo(ComponentState.FINISHED);
            assertThat(componentResult1.getEndDate()).isNotNull();
        });
    }

    /**
     * Test PublixUtils.startComponent(): after reloading the same component a
     * new component result should be created and the old one should be finished
     */
    @Test
    public void checkStartComponentFinishReloadableComponentResult()
            throws IOException, ForbiddenReloadException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long studyResultId = createStudyResultAndStartFirstComponent(study);

        // Start the same (first) component a second time
        startComponentByPosition(1, study, studyResultId);

        // Check
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            ComponentResult componentResult1 = studyResult
                    .getComponentResultList().get(0);
            ComponentResult componentResult2 = studyResult
                    .getComponentResultList().get(1);

            // Check second ComponentResult
            assertThat(componentResult2.getComponentState())
                    .isEqualTo(ComponentState.STARTED);

            // Check that prior ComponentResult was finished properly
            assertThat(componentResult1.getComponentState())
                    .isEqualTo(ComponentState.RELOADED);
            assertThat(studyResult.getComponentResultList().get(0).getEndDate())
                    .isNotNull();
        });
    }

    /**
     * Test PublixUtils.startComponent(): if one tries to reload a
     * non-reloadable component, an ForbiddenReloadException should be thrown
     * and the first component result should be finished
     */
    @Test
    public void checkStartComponentNotReloadable()
            throws IOException, ForbiddenReloadException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Create a StudyResult and set the first component as not reloadable
        long studyResultId = jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            StudyResult studyResult = resultCreator.createStudyResult(study,
                    study.getDefaultBatch(), admin.getWorker());
            study.getFirstComponent().setReloadable(false);
            try {
                publixUtils.startComponent(study.getFirstComponent(),
                        studyResult);
            } catch (ForbiddenReloadException e) {
                throw new RuntimeException(e);
            }
            return studyResult.getId();
        });

        // Start the same component a second times, but first is not reloadable
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            try {
                publixUtils.startComponent(study.getComponent(1), studyResult);
                Fail.fail();
            } catch (ForbiddenReloadException e) {
                assertThat(e.getMessage()).isEqualTo(
                        PublixErrorMessages.componentNotAllowedToReload(
                                study.getId(), study.getComponent(1).getId()));
            }
        });

        // Check
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            ComponentResult componentResult1 = studyResult
                    .getComponentResultList().get(0);

            // No second ComponentResult created
            assertThat(studyResult.getComponentResultList()).hasSize(1);

            // Check that prior ComponentResult was finished properly
            assertThat(componentResult1.getComponentState())
                    .isEqualTo(ComponentState.FAIL);
            assertThat(componentResult1.getEndDate()).isNotNull();
        });
    }

    /**
     * Test PublixUtils.abortStudy(): check normal functioning (e.g. all study
     * and component data should be empty also they were filled before)
     */
    @Test
    public void checkAbortStudy() throws IOException, ForbiddenReloadException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Create a StudyResult and study session data.
        long studyResultId = jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            StudyResult studyResult = resultCreator.createStudyResult(study,
                    study.getDefaultBatch(), admin.getWorker());
            studyResult.setStudySessionData("{\"test\":\"test\"}");
            studyResultDao.update(studyResult);
            return studyResult.getId();
        });

        // Now start the first 2 components and both times set result data
        startComponentAndSetData(study, studyResultId, 1, "test data 1");
        startComponentAndSetData(study, studyResultId, 2, "test data 2");

        // Abort
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            publixUtils.abortStudy("abort message", studyResult);
        });

        // Check
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            ComponentResult componentResult1 = studyResult
                    .getComponentResultList().get(0);
            ComponentResult componentResult2 = studyResult
                    .getComponentResultList().get(1);

            assertThat(componentResult1.getComponentState())
                    .isEqualTo(ComponentState.ABORTED);
            assertThat(componentResult1.getData()).isNullOrEmpty();
            assertThat(componentResult2.getComponentState())
                    .isEqualTo(ComponentState.ABORTED);
            assertThat(componentResult2.getData()).isNullOrEmpty();
            assertThat(studyResult.getStudyState())
                    .isEqualTo(StudyResult.StudyState.ABORTED);
            assertThat(studyResult.getAbortMsg()).isEqualTo("abort message");
            assertThat(studyResult.getEndDate()).isNotNull();
            assertThat(studyResult.getStudySessionData()).isNullOrEmpty();
        });
    }

    /**
     * Test PublixUtils.finishStudyResult(): normal functioning and finish
     * successful
     */
    @Test
    public void checkFinishStudyResultSuccessful()
            throws IOException, ForbiddenReloadException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Start a study and the first 2 components and both times set result
        // data
        long studyResultId = createStudyResult(study);
        startComponentAndSetData(study, studyResultId, 1, "test data 1");
        startComponentAndSetData(study, studyResultId, 2, "test data 2");

        // Now finish the study successfully with an error message
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            publixUtils.finishStudyResult(true, "error message", studyResult);
        });

        // Check
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            ComponentResult componentResult1 = studyResult
                    .getComponentResultList().get(0);
            ComponentResult componentResult2 = studyResult
                    .getComponentResultList().get(1);

            // Check component results: all should be finished
            assertThat(componentResult1.getComponentState())
                    .isEqualTo(ComponentState.FINISHED);
            assertThat(componentResult1.getData()).isEqualTo("test data 1");
            assertThat(componentResult2.getComponentState())
                    .isEqualTo(ComponentState.FINISHED);
            assertThat(componentResult2.getData()).isEqualTo("test data 2");

            // Check study result
            assertThat(studyResult.getStudyState())
                    .isEqualTo(StudyState.FINISHED);
            // Not possible to check confirmation code because it depends on the
            // worker and can be null
            assertThat(studyResult.getErrorMsg()).isEqualTo("error message");
            assertThat(studyResult.getEndDate()).isNotNull();
            assertThat(studyResult.getStudySessionData()).isNullOrEmpty();
        });
    }

    /**
     * Test PublixUtils.finishStudyResult(): call the method with functioning
     * and finish successful
     */
    @Test
    public void checkFinishStudyResultFailed()
            throws IOException, ForbiddenReloadException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Start a study and the first 2 components and both times set result
        // data
        long studyResultId = createStudyResult(study);
        startComponentAndSetData(study, studyResultId, 1, "test data 1");
        startComponentAndSetData(study, studyResultId, 2, "test data 2");

        // Now finish the study unsuccessfully with an error message
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            publixUtils.finishStudyResult(false, "error message", studyResult);
        });

        // Check
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            ComponentResult componentResult1 = studyResult
                    .getComponentResultList().get(0);
            ComponentResult componentResult2 = studyResult
                    .getComponentResultList().get(1);

            // Check component results: first one should be finished last one
            // started (but not failed)
            assertThat(componentResult1.getComponentState())
                    .isEqualTo(ComponentState.FINISHED);
            assertThat(componentResult1.getData()).isEqualTo("test data 1");
            assertThat(componentResult2.getComponentState())
                    .isEqualTo(ComponentState.STARTED);
            assertThat(componentResult2.getData()).isEqualTo("test data 2");

            // Check study result
            assertThat(studyResult.getStudyState()).isEqualTo(StudyState.FAIL);
            assertThat(studyResult.getConfirmationCode()).isNull();
            assertThat(studyResult.getErrorMsg()).isEqualTo("error message");
            assertThat(studyResult.getEndDate()).isNotNull();
            assertThat(studyResult.getStudySessionData()).isNullOrEmpty();
        });
    }

    /**
     * Test PublixUtils.finishAbandonedStudyResults: if there are exactly the
     * max allowed number of ID cookies than the oldest cookie should be deleted
     */
    @Test
    public void checkFinishAbandonedStudyResultsEqualAllowed()
            throws InternalServerErrorPublixException,
            BadRequestPublixException {
        List<Cookie> cookieList = generateIdCookieList(
                IdCookieCollection.MAX_ID_COOKIES);
        testHelper.mockContext(cookieList);

        jpaApi.withTransaction(() -> {
            try {
                publixUtils.finishAbandonedStudyResults();
            } catch (PublixException e) {
                throw new RuntimeException(e);
            }
        });

        // Check that oldest Id cookie is gone (the one with ID 1l)
        try {
            idCookieService.getIdCookie(1l);
            Fail.fail();
        } catch (BadRequestPublixException e) {
            // just throwing the exception is enough
        }

        // Check that all other ID cookies are still there
        checkRangeOfIdCookiesExist(2, IdCookieCollection.MAX_ID_COOKIES);
    }

    /**
     * Test PublixUtils.finishAbandonedStudyResults: if there are more ID
     * cookies than the max allowed number than the oldest cookie should be
     * deleted (this case should actually never happen in live - there shouldn't
     * be more than the max allowed number of ID cookies).
     */
    @Test
    public void checkFinishAbandonedStudyResultsMoreThanAllowed()
            throws InternalServerErrorPublixException,
            BadRequestPublixException {
        List<Cookie> cookieList = generateIdCookieList(
                IdCookieCollection.MAX_ID_COOKIES + 1);
        testHelper.mockContext(cookieList);

        jpaApi.withTransaction(() -> {
            try {
                publixUtils.finishAbandonedStudyResults();
            } catch (PublixException e) {
                throw new RuntimeException(e);
            }
        });

        // Check that oldest Id cookie is gone (the one with ID 1l)
        try {
            idCookieService.getIdCookie(1l);
            Fail.fail();
        } catch (BadRequestPublixException e) {
            // just throwing the exception is enough
        }

        // Check that all other ID cookies are still there
        checkRangeOfIdCookiesExist(2, IdCookieCollection.MAX_ID_COOKIES + 1);
    }

    /**
     * Test PublixUtils.finishAbandonedStudyResults: if there are less ID
     * cookies than the max allowed number than all ID cookies should be kept
     */
    @Test
    public void checkFinishAbandonedStudyResultsNoDeleting()
            throws BadRequestPublixException,
            InternalServerErrorPublixException {
        List<Cookie> cookieList = generateIdCookieList(
                IdCookieCollection.MAX_ID_COOKIES - 1);
        testHelper.mockContext(cookieList);

        jpaApi.withTransaction(() -> {
            try {
                publixUtils.finishAbandonedStudyResults();
            } catch (PublixException e) {
                throw new RuntimeException(e);
            }
        });

        // Check that all ID cookies are still there
        checkRangeOfIdCookiesExist(1, IdCookieCollection.MAX_ID_COOKIES - 1);
    }

    /**
     * Test PublixUtils.finishAbandonedStudyResults: function should work even
     * if there are no ID cookies yet
     */
    @Test
    public void checkFinishAbandonedStudyResultsEmpty()
            throws IOException, PublixException {
        // Generate empty ID cookie list
        List<Cookie> cookieList = new ArrayList<>();
        testHelper.mockContext(cookieList);

        jpaApi.withTransaction(() -> {
            try {
                publixUtils.finishAbandonedStudyResults();
            } catch (PublixException e) {
                throw new RuntimeException(e);
            }
        });

        // Check that there is still no ID cookie
        assertThat(idCookieService.getOldestIdCookie()).isNull();
    }

    /**
     * Checks the normal functioning of PublixUtils.retrieveStudyResult():
     * should return the correct study result
     */
    @Test
    public void checkRetrieveStudyResult() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long studyResultId1 = createStudyResult(study);
        long studyResultId2 = createStudyResult(study);

        jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            StudyResult studyResult1 = studyResultDao.findById(studyResultId1);
            StudyResult studyResult2 = studyResultDao.findById(studyResultId2);

            StudyResult persistedStudyResult1;
            try {
                persistedStudyResult1 = publixUtils.retrieveStudyResult(
                        admin.getWorker(), study, studyResultId1);
                assertThat(persistedStudyResult1).isEqualTo(studyResult1);
                StudyResult persistedStudyResult2 = publixUtils
                        .retrieveStudyResult(admin.getWorker(), study,
                                studyResultId2);
                assertThat(persistedStudyResult2).isEqualTo(studyResult2);
            } catch (PublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Tests PublixUtils.retrieveStudyResult(): It should throw an
     * ForbiddenPublixException if the requested study result doesn't belong to
     * the given study
     */
    @Test
    public void checkRetrieveStudyResultNotFromThisStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long studyResultId1 = createStudyResult(study);

        // We need a second study
        Study clone = createClone(study);

        jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);

            try {
                publixUtils.retrieveStudyResult(admin.getWorker(), clone,
                        studyResultId1);
                Fail.fail();
            } catch (ForbiddenPublixException e) {
                assertThat(e.getMessage()).isEqualTo(
                        PublixErrorMessages.STUDY_RESULT_DOESN_T_BELONG_TO_THIS_STUDY);
            } catch (BadRequestPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Tests PublixUtils.retrieveStudyResult(): Any study result is associated
     * with a worker. If the wrong worker wants to retrieve the result a
     * ForbiddenPublixException must be thrown.
     */
    @Test
    public void checkRetrieveStudyResultNotFromThisWorker() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long studyResultId1 = createStudyResult(study);

        // Create another worker (type is not important here)
        Worker worker = jpaApi.withTransaction(() -> {
            JatosWorker w = new JatosWorker();
            workerDao.create(w);
            return w;
        });

        jpaApi.withTransaction(() -> {
            try {
                publixUtils.retrieveStudyResult(worker, study, studyResultId1);
                Fail.fail();
            } catch (ForbiddenPublixException e) {
                assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                        .workerNeverDidStudy(worker, study.getId()));
            } catch (BadRequestPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Tests PublixUtils.retrieveStudyResult(): should throw a
     * BadRequestPublixException if the study result isn't present in the DB
     */
    @Test
    public void checkRetrieveStudyResultNeverDidStudy()
            throws IOException, PublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Never started any study
        jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            try {
                publixUtils.retrieveStudyResult(admin.getWorker(), study, 1l);
                Fail.fail();
            } catch (BadRequestPublixException e) {
                assertThat(e.getMessage()).isEqualTo(
                        PublixErrorMessages.STUDY_RESULT_DOESN_T_EXIST);
            } catch (ForbiddenPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Tests PublixUtils.retrieveStudyResult(): should throw a
     * ForbiddenPublixException if the study result is already done
     */
    @Test
    public void checkRetrieveStudyResultAlreadyFinished() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long studyResultId1 = createStudyResult(study);

        jpaApi.withTransaction(() -> {
            StudyResult studyResult1 = studyResultDao.findById(studyResultId1);
            publixUtils.finishStudyResult(true, null, studyResult1);
        });

        jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            try {
                publixUtils.retrieveStudyResult(admin.getWorker(), study,
                        studyResultId1);
                Fail.fail();
            } catch (ForbiddenPublixException e) {
                assertThat(e.getMessage()).isEqualTo(
                        PublixErrorMessages.workerFinishedStudyAlready(
                                admin.getWorker(), study.getId()));
            } catch (BadRequestPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Tests PublixUtils.retrieveLastComponentResult(): check that the last
     * component result is returned
     */
    @Test
    public void checkRetrieveLastComponentResult() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Start a study and its first two components
        long studyResultId = createStudyResult(study);
        startComponentByPosition(1, study, studyResultId);
        long componentResultId2 = startComponentByPosition(2, study,
                studyResultId);

        // Check that the second result is returned
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            // Get the second component result
            ComponentResult componentResult2 = componentResultDao
                    .findById(componentResultId2);

            ComponentResult retrievedComponentResult = publixUtils
                    .retrieveLastComponentResult(studyResult);
            assertThat(retrievedComponentResult).isEqualTo(componentResult2);
        });
    }

    /**
     * Tests PublixUtils.retrieveLastComponentResult(): if no component result
     * exist null should be returned
     */
    @Test
    public void checkRetrieveLastComponentResultEmpty() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        long studyResultId = createStudyResult(study);

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            // Check that null is returned
            ComponentResult retrievedComponentResult = publixUtils
                    .retrieveLastComponentResult(studyResult);
            assertThat(retrievedComponentResult).isNull();
        });
    }

    /**
     * Tests PublixUtils.retrieveLastComponent(): check that the last component
     * is returned
     */
    @Test
    public void checkRetrieveLastComponent() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Start a study and its first two components
        long studyResultId = createStudyResult(study);
        startComponentByPosition(1, study, studyResultId);
        startComponentByPosition(2, study, studyResultId);

        // Check that the second result is returned
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            Component retrievedComponent = publixUtils
                    .retrieveLastComponent(studyResult);
            assertThat(retrievedComponent).isEqualTo(study.getComponent(2));
        });
    }

    /**
     * Tests PublixUtils.retrieveLastComponent(): if no component exist null
     * should be returned
     */
    @Test
    public void checkRetrieveLastComponentEmpty() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long studyResultId = createStudyResult(study);

        // Check that null is returned
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            Component retrievedComponent = publixUtils
                    .retrieveLastComponent(studyResult);
            assertThat(retrievedComponent).isNull();
        });
    }

    /**
     * Tests PublixUtils.retrieveCurrentComponentResult(): check that the last
     * component result is returned if it is not 'done'
     */
    @Test
    public void checkRetrieveCurrentComponentResult() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Start a study and its first two components
        long studyResultId = createStudyResult(study);
        startComponentByPosition(1, study, studyResultId);
        long componentResultId2 = startComponentByPosition(2, study,
                studyResultId);

        // Check that the second result is returned
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            // Get the second component result
            ComponentResult componentResult2 = componentResultDao
                    .findById(componentResultId2);
            ComponentResult retrievedComponentResult = publixUtils
                    .retrieveCurrentComponentResult(studyResult);
            assertThat(retrievedComponentResult).isEqualTo(componentResult2);
        });
    }

    /**
     * Tests PublixUtils.retrieveCurrentComponentResult(): check that null is
     * returned if the last component result is 'done'
     */
    @Test
    public void checkRetrieveCurrentComponentResultIfDone() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Start a study and its first two components
        long studyResultId = createStudyResult(study);
        startComponentByPosition(1, study, studyResultId);
        long componentResultId2 = startComponentByPosition(2, study,
                studyResultId);

        // Set the second ComponentResult to FINISHED
        jpaApi.withTransaction(() -> {
            ComponentResult componentResult2 = componentResultDao
                    .findById(componentResultId2);
            componentResult2.setComponentState(ComponentState.FINISHED);
            componentResultDao.update(componentResult2);
        });

        // Check that null is returned
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            ComponentResult retrievedComponentResult = publixUtils
                    .retrieveCurrentComponentResult(studyResult);
            assertThat(retrievedComponentResult).isNull();
        });
    }

    /**
     * Tests PublixUtils.retrieveStartedComponentResult(): check that the last
     * component result is returned if it is not 'done'
     */
    @Test
    public void checkRetrieveStartedComponentResult() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Start a study and its first two components
        long studyResultId = createStudyResult(study);
        startComponentByPosition(1, study, studyResultId);
        long componentResultId2 = startComponentByPosition(2, study,
                studyResultId);

        // Check that the second result is returned since it is not 'done' -
        // even if the one 'asked' for is the third component
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            ComponentResult componentResult2 = componentResultDao
                    .findById(componentResultId2);
            try {
                ComponentResult retrievedComponentResult = publixUtils
                        .retrieveStartedComponentResult(study.getComponent(3),
                                studyResult);
                assertThat(retrievedComponentResult)
                        .isEqualTo(componentResult2);
            } catch (ForbiddenReloadException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Tests PublixUtils.retrieveStartedComponentResult(): check that a new
     * component result is returned if the last one is 'done'
     */
    @Test
    public void checkRetrieveStartedComponentResultDone() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Start a study and its first two components
        long studyResultId = createStudyResult(study);
        startComponentByPosition(1, study, studyResultId);
        long componentResultId2 = startComponentByPosition(2, study,
                studyResultId);

        // Set the second ComponentResult to FINISHED
        jpaApi.withTransaction(() -> {
            ComponentResult componentResult2 = componentResultDao
                    .findById(componentResultId2);
            componentResult2.setComponentState(ComponentState.FINISHED);
            componentResultDao.update(componentResult2);
        });

        // Check that a new component result for the 2rd component is returned
        // since the last one is 'done'
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            ComponentResult componentResult2 = componentResultDao
                    .findById(componentResultId2);
            try {
                ComponentResult retrievedComponentResult = publixUtils
                        .retrieveStartedComponentResult(study.getComponent(2),
                                studyResult);
                assertThat(retrievedComponentResult)
                        .isNotEqualTo(componentResult2);
                assertThat(retrievedComponentResult.getComponent())
                        .isEqualTo(study.getComponent(2));
            } catch (ForbiddenReloadException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Tests PublixUtils.retrieveFirstActiveComponent(): normal functioning
     */
    @Test
    public void checkRetrieveFirstActiveComponent() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            Component component = study.getFirstComponent();
            component.setActive(false);
            componentDao.update(component);
        });

        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                Component component = publixUtils
                        .retrieveFirstActiveComponent(s);
                assertThat(component).isEqualTo(s.getComponent(2));
            } catch (NotFoundPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Tests PublixUtils.retrieveFirstActiveComponent(): if there is no active
     * component an NotFoundPublixException should be thrown
     */
    @Test
    public void checkRetrieveFirstActiveComponentNotFound() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Set all component to not active
        jpaApi.withTransaction(() -> {
            for (Component component : study.getComponentList()) {
                component.setActive(false);
                componentDao.update(component);
            }
        });

        jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            try {
                publixUtils.retrieveFirstActiveComponent(s);
                Fail.fail();
            } catch (NotFoundPublixException e) {
                assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                        .studyHasNoActiveComponents(s.getId()));
            }
        });
    }

    /**
     * Test PublixUtils.retrieveNextActiveComponent(): normal functioning
     */
    @Test
    public void checkRetrieveNextActiveComponent() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long studyResultId = createStudyResultAndStartFirstComponent(study);

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            Component component = publixUtils
                    .retrieveNextActiveComponent(studyResult);
            // Next component is the 2nd
            assertThat(component).isEqualTo(study.getComponent(2));
        });
    }

    /**
     * Test PublixUtils.retrieveNextActiveComponent(): no next active component
     * can be found
     */
    @Test
    public void checkRetrieveNextActiveComponentNotFound() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Set all component to not active
        jpaApi.withTransaction(() -> {
            for (Component component : study.getComponentList()) {
                component.setActive(false);
                componentDao.update(component);
            }
        });

        long studyResultId = createStudyResultAndStartFirstComponent(study);

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            Component component = publixUtils
                    .retrieveNextActiveComponent(studyResult);
            // Since all components are not active it should be null
            assertThat(component).isNull();
        });
    }

    /**
     * Test PublixUtils.retrieveComponent(): normal functioning
     */
    @Test
    public void checkRetrieveComponent() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Retrieve last component
        jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            try {
                Component component = publixUtils.retrieveComponent(s,
                        s.getLastComponent().getId());
                assertThat(component).isEqualTo(s.getLastComponent());
            } catch (NotFoundPublixException | BadRequestPublixException
                    | ForbiddenPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Test PublixUtils.retrieveComponent(): If an component with this ID
     * doesn't exist for this study an NotFoundPublixException should be thrown
     */
    @Test
    public void checkRetrieveComponentWrongId() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            try {
                publixUtils.retrieveComponent(study, 999l);
                Fail.fail();
            } catch (NotFoundPublixException e) {
                assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                        .componentNotExist(study.getId(), 999l));
            } catch (BadRequestPublixException | ForbiddenPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Test PublixUtils.retrieveComponent(): If the component doesn't belong to
     * this study an BadRequestPublixException should be thrown
     */
    @Test
    public void checkRetrieveComponentNotOfThisStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Study clone = createClone(study);

        jpaApi.withTransaction(() -> {
            try {
                publixUtils.retrieveComponent(study,
                        clone.getFirstComponent().getId());
                Fail.fail();
            } catch (BadRequestPublixException e) {
                assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                        .componentNotBelongToStudy(study.getId(),
                                clone.getFirstComponent().getId()));
            } catch (NotFoundPublixException | ForbiddenPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Test PublixUtils.retrieveComponent(): If the component isn't active an
     * ForbiddenPublixException should be thrown
     */
    @Test
    public void checkRetrieveComponentNotActive() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            Component component = study.getFirstComponent();
            component.setActive(false);
            componentDao.update(component);
        });

        jpaApi.withTransaction(() -> {
            try {
                publixUtils.retrieveComponent(study,
                        study.getFirstComponent().getId());
                Fail.fail();
            } catch (ForbiddenPublixException e) {
                assertThat(e.getMessage()).isEqualTo(
                        PublixErrorMessages.componentNotActive(study.getId(),
                                study.getFirstComponent().getId()));
            } catch (NotFoundPublixException | BadRequestPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Test retrieveComponentByPosition(): normal functioning
     */
    @Test
    public void checkRetrieveComponentByPosition() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            try {
                Component component = publixUtils
                        .retrieveComponentByPosition(study.getId(), 1);
                assertThat(component).isEqualTo(study.getFirstComponent());
            } catch (PublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Test retrieveComponentByPosition(): if the position parameter must is
     * null a BadRequestPublixException must be thrown
     */
    @Test
    public void checkRetrieveComponentByPositionNull() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            try {
                publixUtils.retrieveComponentByPosition(study.getId(), null);
                Fail.fail();
            } catch (BadRequestPublixException e) {
                assertThat(e.getMessage()).isEqualTo(
                        PublixErrorMessages.COMPONENTS_POSITION_NOT_NULL);
            } catch (PublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Test retrieveComponentByPosition(): if there is no component at this
     * position an NotFoundPublixException should be thrown
     */
    @Test
    public void checkRetrieveComponentByPositionWrong() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            try {
                publixUtils.retrieveComponentByPosition(study.getId(), 999);
                Fail.fail();
            } catch (NotFoundPublixException e) {
                assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                        .noComponentAtPosition(study.getId(), 999));
            } catch (BadRequestPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Test PublixUtils.retrieveStudy(): normal functioning
     */
    @Test
    public void checkRetrieveStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            try {
                Study retrievedStudy = publixUtils.retrieveStudy(study.getId());
                assertThat(retrievedStudy).isEqualTo(study);
            } catch (NotFoundPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Test PublixUtils.retrieveStudy(): if a study with this ID doesn't exist
     * in DB a NotFoundPublixException should be thrown
     */
    @Test
    public void checkRetrieveStudyNotFound() throws IOException {
        testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            try {
                publixUtils.retrieveStudy(999l);
                Fail.fail();
            } catch (NotFoundPublixException e) {
                assertThat(e.getMessage())
                        .isEqualTo(PublixErrorMessages.studyNotExist(999l));
            }
        });
    }

    /**
     * PublixUtils.checkComponentBelongsToStudy(): normal functioning - if the
     * component belongs to the study the method should just return
     */
    @Test
    public void checkCheckComponentBelongsToStudy()
            throws IOException, PublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                publixUtils.checkComponentBelongsToStudy(s,
                        s.getFirstComponent());
            } catch (BadRequestPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * PublixUtils.checkComponentBelongsToStudy(): if the component does not
     * belong to the study the method should throw a BadRequestPublixException
     */
    @Test
    public void checkCheckComponentBelongsToStudyFail()
            throws IOException, PublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Study clone = createClone(study);

        // Check if component of 'clone' belongs to 'study'
        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                Study c = studyDao.findById(clone.getId());
                publixUtils.checkComponentBelongsToStudy(s,
                        c.getFirstComponent());
                Fail.fail();
            } catch (BadRequestPublixException e) {
                assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
                        .componentNotBelongToStudy(study.getId(),
                                clone.getFirstComponent().getId()));
            }
        });
    }

    /**
     * PublixUtils.checkStudyIsGroupStudy()
     */
    @Test
    public void checkCheckStudyIsGroupStudy()
            throws IOException, PublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            s.setGroupStudy(true);
            studyDao.update(s);
        });

        // Since it's a group study the method should just return
        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                publixUtils.checkStudyIsGroupStudy(s);
            } catch (ForbiddenPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * PublixUtils.checkStudyIsGroupStudy()
     */
    @Test
    public void checkCheckStudyIsGroupStudyFalse()
            throws IOException, PublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            s.setGroupStudy(false);
            studyDao.update(s);
        });

        // Since it's not a group study the method should just throw an
        // exception
        jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                publixUtils.checkStudyIsGroupStudy(s);
                Fail.fail();
            } catch (ForbiddenPublixException e) {
                // Just an exception is fine
            }
        });
    }

    /**
     * PublixUtils.retrieveBatchByIdOrDefault(): get default batch if batch ID
     * is -1
     */
    @Test
    public void checkRetrieveBatchByIdOrDefaultDefault()
            throws IOException, PublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            Batch retrievedBatch = publixUtils.retrieveBatchByIdOrDefault(-1l,
                    study);
            assertThat(retrievedBatch).isEqualTo(study.getDefaultBatch());
        });
    }

    /**
     * PublixUtils.retrieveBatchByIdOrDefault(): get batch specified by ID
     */
    @Test
    public void checkRetrieveBatchByIdOrDefaultById()
            throws IOException, PublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long batchId = jpaApi.withTransaction(() -> {
            Batch batch2 = batchService.clone(study.getDefaultBatch());
            batch2.setTitle("Test Title");
            batchService.createAndPersistBatch(batch2, study);
            return batch2.getId();
        });

        jpaApi.withTransaction(() -> {
            Batch batch = batchDao.findById(batchId);
            Batch retrievedBatch = publixUtils
                    .retrieveBatchByIdOrDefault(batch.getId(), study);
            assertThat(retrievedBatch).isEqualTo(batch);
        });
    }

    /**
     * PublixUtils.retrieveBatch(): get batch specified by ID
     */
    @Test
    public void checkRetrieveBatch() throws IOException, PublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long batchId = jpaApi.withTransaction(() -> {
            Batch batch2 = batchService.clone(study.getDefaultBatch());
            batch2.setTitle("Test Title");
            batchService.createAndPersistBatch(batch2, study);
            return batch2.getId();
        });

        jpaApi.withTransaction(() -> {
            try {
                Batch batch = batchDao.findById(batchId);
                Batch retrievedBatch = publixUtils.retrieveBatch(batch.getId());
                assertThat(retrievedBatch).isEqualTo(batch);
            } catch (ForbiddenPublixException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * PublixUtils.retrieveBatch(): if a batch with the specified ID doesn't
     * exist throw an ForbiddenPublixException
     */
    @Test
    public void checkRetrieveBatchFail() throws IOException, PublixException {
        testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            try {
                publixUtils.retrieveBatch(999l);
                Fail.fail();
            } catch (ForbiddenPublixException e) {
                // Just an exception is fine
            }
        });
    }

    /**
     * PublixUtils.setPreStudyStateByPre()
     */
    @Test
    public void checkSetPreStudyStateByPre()
            throws IOException, PublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long studyResultId = createStudyResult(study);

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            publixUtils.setPreStudyStateByPre(true, studyResult);
            assertThat(studyResult.getStudyState()).isEqualTo(StudyState.PRE);
            publixUtils.setPreStudyStateByPre(false, studyResult);
            assertThat(studyResult.getStudyState())
                    .isEqualTo(StudyState.STARTED);
        });
    }

    /**
     * PublixUtils.setPreStudyStateByComponentId(): should set study result's to
     * STARTED only and only if the state is originally in PRE and it is not the
     * first component
     */
    @Test
    public void checkSetPreStudyStateByComponentId()
            throws IOException, PublixException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        long studyResultId = createStudyResult(study);

        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);

            // PRE && first => stays in PRE
            studyResult.setStudyState(StudyState.PRE);
            publixUtils.setPreStudyStateByComponentId(studyResult, study,
                    study.getFirstComponent().getId());
            assertThat(studyResult.getStudyState()).isEqualTo(StudyState.PRE);

            // STARTED && first => keeps state
            studyResult.setStudyState(StudyState.STARTED);
            publixUtils.setPreStudyStateByComponentId(studyResult, study,
                    study.getFirstComponent().getId());
            assertThat(studyResult.getStudyState())
                    .isEqualTo(StudyState.STARTED);

            // PRE && second => changes to STARTED
            studyResult.setStudyState(StudyState.PRE);
            publixUtils.setPreStudyStateByComponentId(studyResult, study,
                    study.getComponent(2).getId());
            assertThat(studyResult.getStudyState())
                    .isEqualTo(StudyState.STARTED);

            // STARTED && second => keeps state
            studyResult.setStudyState(StudyState.STARTED);
            publixUtils.setPreStudyStateByComponentId(studyResult, study,
                    study.getComponent(2).getId());
            assertThat(studyResult.getStudyState())
                    .isEqualTo(StudyState.STARTED);
        });
    }

    private long createStudyResultAndStartFirstComponent(Study study) {
        return jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            StudyResult studyResult = resultCreator.createStudyResult(study,
                    study.getDefaultBatch(), admin.getWorker());
            try {
                publixUtils.startComponent(study.getFirstComponent(),
                        studyResult);
            } catch (ForbiddenReloadException e) {
                throw new RuntimeException(e);
            }
            return studyResult.getId();
        });
    }

    private long startComponentByPosition(int position, Study study,
            long studyResultId) {
        return jpaApi.withTransaction(() -> {
            try {
                StudyResult studyResult = studyResultDao
                        .findById(studyResultId);
                ComponentResult componentResult = publixUtils.startComponent(
                        study.getComponent(position), studyResult);
                return componentResult.getId();
            } catch (ForbiddenReloadException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private long createStudyResult(Study study) {
        return jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            StudyResult studyResult = resultCreator.createStudyResult(study,
                    study.getDefaultBatch(), admin.getWorker());
            return studyResult.getId();
        });
    }

    private void startComponentAndSetData(Study study, long studyResultId,
            int position, String data) {
        jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            try {
                ComponentResult componentResult = publixUtils.startComponent(
                        study.getComponent(position), studyResult);
                componentResult.setData(data);
                componentResultDao.update(componentResult);
            } catch (ForbiddenReloadException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private List<Cookie> generateIdCookieList(int size) {
        List<Cookie> cookieList = new ArrayList<>();
        for (long i = 1l; i <= size; i++) {
            IdCookieModel idCookie = idCookieTestHelper.buildDummyIdCookie(i);
            cookieList.add(idCookieTestHelper.buildCookie(idCookie));
        }
        return cookieList;
    }

    private void checkRangeOfIdCookiesExist(int from, int to)
            throws BadRequestPublixException,
            InternalServerErrorPublixException {
        for (long i = from; i <= to; i++) {
            idCookieService.getIdCookie(i);
        }
    }

    private Study createClone(Study study) {
        Study clone = jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
            try {
                Study c = studyService.clone(study);
                studyService.createAndPersistStudy(admin, c);
                return c;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return clone;
    }

}
