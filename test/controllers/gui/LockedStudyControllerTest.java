package controllers.gui;

import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.publix.ForbiddenNonLinearFlowException;
import exceptions.publix.ForbiddenReloadException;
import general.TestHelper;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.JatosWorker;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.libs.Json;
import play.mvc.Call;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.StudyService;
import services.gui.UserService;
import services.publix.ResultCreator;
import services.publix.workers.JatosPublixUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.route;

/**
 * A study can be locked. Then it shouldn't be possible to change its
 * properties, or its component's properties, or its batch's properties.
 *
 * @author Kristian Lange
 */
public class LockedStudyControllerTest {

    private Injector injector;

    @Inject
    private Application fakeApplication;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private StudyDao studyDao;

    @Inject
    private UserDao userDao;

    @Inject
    private StudyService studyService;

    @Inject
    private ResultCreator resultCreator;

    @Inject
    private JatosPublixUtils jatosPublixUtils;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void startApp() throws Exception {
        fakeApplication = Helpers.fakeApplication();

        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        injector = Guice.createInjector(builder.applicationModule());
        injector.injectMembers(this);

        Helpers.start(fakeApplication);
    }

    @After
    public void stopApp() throws Exception {
        // Clean up
        testHelper.removeAllStudies();

        Helpers.stop(fakeApplication);
        testHelper.removeStudyAssetsRootDir();
        testHelper.removeAllStudyLogs();
    }

    /**
     * Checks that the given Call and method cause a JatosGuiException with the
     * HTTP status 403.
     */
    private void checkForbiddenBecauseLocked(Call call, String method) {
        Http.Session session = testHelper.mockSessionCookieAndCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder().method(method)
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(call.url());
        testHelper.assertJatosGuiException(request, Http.Status.FORBIDDEN, "");
    }

    private void lockStudy(Study study) {
        jpaApi.withTransaction(() -> {
            study.setLocked(true);
            studyDao.update(study);
        });
    }

    /**
     * Check that Batches.submitCreated() doesn't work if study is locked
     */
    @Test
    public void callBatchesSubmitCreated() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Batches.submitCreated(study.getId());
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that Batches.submitEditedProperties() doesn't work if study is
     * locked
     */
    @Test
    public void callBatchesSubmitEditedProperties() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Batches.submitEditedProperties(
                study.getId(), study.getDefaultBatch().getId());
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that Batches.toggleActive() doesn't work if study is locked
     */
    @Test
    public void callBatchesToggleActive() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Batches.toggleActive(study.getId(),
                study.getDefaultBatch().getId(), true);
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that Batches.toggleAllowedWorkerType() doesn't work if study is
     * locked
     */
    @Test
    public void callBatchesToggleAllowedWorkerType() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Batches.toggleAllowedWorkerType(
                study.getId(), study.getDefaultBatch().getId(), JatosWorker.WORKER_TYPE, true);
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that Batches.remove() doesn't work if study is locked
     */
    @Test
    public void callBatchesRemove() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Batches
                .remove(study.getId(), study.getDefaultBatch().getId());
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that Components.submitCreated() doesn't work if study is locked
     */
    @Test
    public void callComponentsSubmitCreated() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Components.submitCreated(study.getId());
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that Components.submitEdited() doesn't work if study is locked
     */
    @Test
    public void callComponentsSubmitEdited() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Components
                .submitEdited(study.getId(), study.getFirstComponent().get().getId());
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that Components.toggleActive() doesn't work if study is locked
     */
    @Test
    public void callComponentsToggleActive() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Components.toggleActive(
                study.getId(), study.getFirstComponent().get().getId(), true);
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that Components.cloneComponent() doesn't work if study is locked
     */
    @Test
    public void callComponentsCloneComponent() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Components.cloneComponent(
                study.getId(), study.getFirstComponent().get().getId());
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that Components.remove() doesn't work if study is locked
     */
    @Test
    public void callComponentsRemove() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Components.remove(study.getId(),
                study.getFirstComponent().get().getId());
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that ImportExport.importComponent() doesn't work if study is locked
     */
    @Test
    public void callImportExportImportComponent() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.ImportExport.importComponent(study.getId());
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that ImportExport.importComponentConfirmed() doesn't work if study
     * is locked
     */
    @Test
    public void callImportExportImportComponentConfirmed() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.ImportExport.importComponentConfirmed(study.getId());
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that Studies.submitEdited doesn't work if study is locked
     */
    @Test
    public void callStudiesSubmitEdited() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Studies.submitEdited(study.getId());
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that Studies.remove doesn't work if study is locked
     */
    @Test
    public void callStudiesRemove() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Studies.remove(study.getId());
        checkForbiddenBecauseLocked(call, Helpers.DELETE);
    }

    /**
     * Check that Studies.changeComponentOrder doesn't work if study is locked
     */
    @Test
    public void callStudiesChangeComponentOrder() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        lockStudy(study);
        Call call = routes.Studies.changeComponentOrder(
                study.getId(), study.getComponent(1).getId(), "1");
        checkForbiddenBecauseLocked(call, Helpers.POST);
    }

    /**
     * Check that ImportExport.exportDataOfComponentResults does still work if study is locked
     */
    @Test
    public void callExportComponentResults() {
        // Create a study with a StudyResult
        long firstComponentResultId = jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            Study study;
            try {
                study = testHelper.importExampleStudy(injector);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            study.setLocked(true);
            studyService.createAndPersistStudy(admin, study);

            StudyResult studyResult = resultCreator.createStudyResult(study,
                    study.getDefaultBatch(), admin.getWorker());
            try {
                jatosPublixUtils.startComponent(study.getFirstComponent().get(), studyResult);
                jatosPublixUtils.startComponent(study.getFirstComponent().get(), studyResult);
            } catch (ForbiddenReloadException | ForbiddenNonLinearFlowException e) {
                throw new RuntimeException(e);
            }
            return studyResult.getComponentResultList().get(0).getId();
        });

        // Export the two result data from the StudyResult
        Http.Session session = testHelper.mockSessionCookieAndCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder()
                .method(Helpers.POST)
                .session(session)
                .bodyJson(Json.parse("{\"resultIds\": [" + firstComponentResultId + "]}"))
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.ImportExport.exportDataOfComponentResults().url());
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(Http.Status.OK);
    }

}
