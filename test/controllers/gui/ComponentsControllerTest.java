package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.ComponentDao;
import general.TestHelper;
import general.common.MessagesStrings;
import models.common.Study;
import models.gui.ComponentProperties;
import org.apache.http.HttpHeaders;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

/**
 * Testing actions of controller.Components.
 *
 * @author Kristian Lange
 */
public class ComponentsControllerTest {

    private Injector injector;

    @Inject
    private static Application fakeApplication;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private ComponentDao componentDao;

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
     * Checks action with route Components.showComponent.
     */
    @Test
    public void callRunComponent() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Http.Session session = testHelper
                .mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder().method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Components.runComponent(study.getId(),
                        study.getComponent(1).getId(), -1L).url());
        Result result = route(request);

        assertEquals(SEE_OTHER, result.status());
        assertThat(result.session().containsKey("jatos_run"));
        assertThat(result.session().containsValue("single_component_start"));
        assertThat(result.session().containsKey("run_component_id"));
        assertThat(result.session().containsValue(study.getId().toString()));
        assertThat(result.headers().get(HttpHeaders.LOCATION).contains("jatosWorkerId"));
    }

    /**
     * Checks action with route Components.showComponent if no html file is set
     * within the Component.
     */
    @Test
    public void callRunComponentNoHtml() throws Exception {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        jpaApi.withTransaction(() -> {
            study.getComponent(1).setHtmlFilePath(null);
            componentDao.update(study.getComponent(1));
        });

        Http.Session session = testHelper
                .mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder().method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Components.runComponent(study.getId(),
                        study.getComponent(1).getId(), -1L).url());
        // Empty html path must lead to an JatosGuiException with a HTTP status of 400
        testHelper.assertJatosGuiException(request, Http.Status.BAD_REQUEST,
                "HTML file path is empty");
    }

    /**
     * Checks action with route Components.create.
     */
    @Test
    public void callProperties() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Http.Session session = testHelper
                .mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder().method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Components
                        .properties(study.getId(), study.getFirstComponent().get().getId()).url());
        Result result = route(request);

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.charset().get()).isEqualTo("UTF-8");
        assertThat(result.contentType().get()).isEqualTo("application/json");

        // Check properties in JSON
        JsonNode node = Json.mapper().readTree(contentAsString(result));
        assertThat(node.get(ComponentProperties.TITLE).toString())
                .isEqualTo("\"" + study.getFirstComponent().get().getTitle() + "\"");
        assertThat(node.get(ComponentProperties.ACTIVE).toString()).isEqualTo(
                String.valueOf(study.getFirstComponent().get().isActive()));
        assertThat(node.get(ComponentProperties.COMMENTS).toString()).isEqualTo(
                "\"" + study.getFirstComponent().get().getComments() + "\"");
        assertThat(node.get(ComponentProperties.HTML_FILE_PATH).toString())
                .isEqualTo("\"" + study.getFirstComponent().get().getHtmlFilePath() + "\"");
        assertThat(node.get(ComponentProperties.JSON_DATA).toString()).contains(
                "This component displays text and reacts to key presses.");
        assertThat(node.get(ComponentProperties.RELOADABLE).toString())
                .isEqualTo(String.valueOf(study.getFirstComponent().get().isReloadable()));
        assertThat(node.get(ComponentProperties.UUID).toString())
                .isEqualTo("\"" + study.getFirstComponent().get().getUuid() + "\"");
        assertThat(node.get(ComponentProperties.ID).toString())
                .isEqualTo(String.valueOf(study.getFirstComponent().get().getId()));
        assertThat(node.get("studyId").toString()).isEqualTo(
                String.valueOf(study.getFirstComponent().get().getStudy().getId()));
        assertThat(node.get("date").toString())
                .isEqualTo(String.valueOf(study.getFirstComponent().get().getDate()));
    }

    /**
     * Checks action with route Components.submit. After the call the study's
     * page should be shown.
     */
    @Test
    public void callSubmitCreated() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Map<String, String> form = new HashMap<>();
        form.put(ComponentProperties.TITLE, "Title Test");
        form.put(ComponentProperties.RELOADABLE, "true");
        form.put(ComponentProperties.HTML_FILE_PATH, "html_file_path_test.html");
        form.put(ComponentProperties.COMMENTS, "Comments test test.");
        form.put(ComponentProperties.JSON_DATA, "{}");

        Http.Session session = testHelper.mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder().method("POST")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .bodyForm(form)
                .uri(routes.Components.submitCreated(study.getId()).url());
        Result result = route(request);

        assertEquals(OK, result.status());
    }

    /**
     * Checks action with route Components.submit. After the call the component
     * itself should be shown.
     */
    @Test
    public void callSubmitEdited() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Map<String, String> form = new HashMap<>();
        form.put(ComponentProperties.TITLE, "Title Test");
        form.put(ComponentProperties.RELOADABLE, "true");
        form.put(ComponentProperties.HTML_FILE_PATH, "html_file_path_test.html");
        form.put(ComponentProperties.COMMENTS, "Comments test test.");
        form.put(ComponentProperties.JSON_DATA, "{}");

        Http.Session session = testHelper.mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder().method("POST")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .bodyForm(form)
                .uri(routes.Components.submitEdited(study.getId(),
                        study.getFirstComponent().get().getId()).url());
        Result result = route(request);

        assertEquals(OK, result.status());
    }

    /**
     * Checks action with route Components.submit with validation error.
     */
    @Test
    public void callSubmitValidationError() throws Exception {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Map<String, String> form = new HashMap<>();
        form.put(ComponentProperties.TITLE, "");
        form.put(ComponentProperties.RELOADABLE, "true");
        form.put(ComponentProperties.HTML_FILE_PATH, "%.test");
        form.put(ComponentProperties.COMMENTS, "Comments test <i>.");
        form.put(ComponentProperties.JSON_DATA, "{");
        form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SAVE_AND_RUN);

        Http.Session session = testHelper
                .mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder().method("POST")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .bodyForm(form)
                .uri(routes.Components.submitCreated(study.getId()).url());
        Result result = route(request);

        assertThat(result.contentType().get()).isEqualTo("application/json");
        JsonNode node = Json.mapper().readTree(contentAsString(result));
        assertThat(node.get(ComponentProperties.TITLE).toString())
                .isEqualTo("[\"" + MessagesStrings.MISSING_TITLE + "\"]");
        assertThat(node.get(ComponentProperties.HTML_FILE_PATH).toString())
                .isEqualTo("[\"" + MessagesStrings.NOT_A_VALID_PATH_YOU_CAN_LEAVE_IT_EMPTY + "\"]");
        assertThat(node.get(ComponentProperties.COMMENTS).toString())
                .isEqualTo("[\"" + MessagesStrings.NO_HTML_ALLOWED + "\"]");
        assertThat(node.get(ComponentProperties.JSON_DATA).toString())
                .isEqualTo("[\"" + MessagesStrings.INVALID_JSON_FORMAT + "\"]");
    }

    @Test
    public void callChangeProperty() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Http.Session session = testHelper.mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder().method("POST")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Components.toggleActive(study.getId(),
                        study.getComponent(1).getId(), true).url());
        Result result = route(request);

        assertThat(result.status()).isEqualTo(OK);
    }

    @Test
    public void callCloneComponent() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Http.Session session = testHelper.mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder().method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Components.cloneComponent(
                        study.getId(), study.getComponent(1).getId()).url());
        Result result = route(request);

        assertThat(result.status()).isEqualTo(OK);
    }

    @Test
    public void callRemove() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Http.Session session = testHelper.mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder().method("DELETE")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Components
                        .remove(study.getId(), study.getComponent(1).getId()).url());
        Result result = route(request);

        assertThat(result.status()).isEqualTo(OK);
    }

}
