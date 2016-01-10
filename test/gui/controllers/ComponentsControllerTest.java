package gui.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpHeaders;
import org.junit.Test;

import controllers.gui.Components;
import controllers.gui.Users;
import general.AbstractTest;
import models.common.Study;
import models.gui.ComponentProperties;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import services.gui.BreadcrumbsService;

/**
 * Testing actions of controller.Components.
 * 
 * @author Kristian Lange
 */
public class ComponentsControllerTest extends AbstractTest {

	private static Study studyTemplate;

	@Override
	public void before() throws Exception {
		studyTemplate = importExampleStudy();
	}

	@Override
	public void after() throws Exception {
		ioUtils.removeStudyAssetsDir(studyTemplate.getDirName());
	}

	/**
	 * Checks action with route Components.showComponent.
	 */
	@Test
	public void callRunComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(
						controllers.gui.routes.Components
								.runComponent(studyClone.getId(), -1l,
										studyClone.getComponent(1).getId())
								.url());
		Result result = route(request);

		assertEquals(SEE_OTHER, result.status());
		assertThat(result.session().containsKey("jatos_run"));
		assertThat(result.session().containsValue("single_component_start"));
		assertThat(result.session().containsKey("run_component_id"));
		assertThat(
				result.session().containsValue(studyClone.getId().toString()));
		assertThat(result.headers().get(HttpHeaders.LOCATION)
				.contains("jatosWorkerId"));

		// Clean up
		removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.showComponent if no html file is set
	 * within the Component.
	 */
	@Test
	public void callRunComponentNoHtml() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		entityManager.getTransaction().begin();
		studyClone.getComponent(1).setHtmlFilePath(null);
		entityManager.getTransaction().commit();

		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(
						controllers.gui.routes.Components
								.runComponent(studyClone.getId(), -1l,
										studyClone.getComponent(1).getId())
								.url());
		Result result = route(request);

		assertThat(contentAsString(result))
				.contains("HTML file path is empty.");

		removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.create.
	 */
	@Test
	public void callProperties() throws IOException {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(
						controllers.gui.routes.Components
								.properties(studyClone.getId(),
										studyClone.getFirstComponent().getId())
								.url());
		Result result = route(request);
		// TODO check JSON

		assertThat(result.status()).isEqualTo(OK);
		assertThat(contentAsString(result))
				.contains(BreadcrumbsService.NEW_COMPONENT);

		// Clean up
		removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.submit. After the call the study's
	 * page should be shown.
	 */
	@Test
	public void callSubmitCreated() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentProperties.TITLE, "Title Test");
		form.put(ComponentProperties.RELOADABLE, "true");
		form.put(ComponentProperties.HTML_FILE_PATH, "html_file_path_test.html");
		form.put(ComponentProperties.COMMENTS, "Comments test test.");
		form.put(ComponentProperties.JSON_DATA, "{}");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SAVE);
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(form).session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Components
						.submitCreated(studyClone.getId()).url());
		Result result = route(request);

		assertEquals(SEE_OTHER, result.status());

		// Clean up
		removeStudy(studyClone);
	}
	
	//TODO callSubmitEdited

	/**
	 * Checks action with route Components.submit. After the call the component
	 * itself should be shown.
	 */
	@Test
	public void callSubmitAndRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentProperties.TITLE, "Title Test");
		form.put(ComponentProperties.RELOADABLE, "true");
		form.put(ComponentProperties.HTML_FILE_PATH, "html_file_path_test.html");
		form.put(ComponentProperties.COMMENTS, "Comments test test.");
		form.put(ComponentProperties.JSON_DATA, "{}");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SAVE_AND_RUN);
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(form).session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Components
						.submitCreated(studyClone.getId()).url());
		Result result = route(request);

		assertEquals(SEE_OTHER, result.status());
		assertThat(result.headers().get(HttpHeaders.LOCATION)).contains("show");

		// Clean up
		removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.submit with validation error.
	 */
	@Test
	public void callSubmitValidationError() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentProperties.TITLE, "");
		form.put(ComponentProperties.RELOADABLE, "true");
		form.put(ComponentProperties.HTML_FILE_PATH, "");
		form.put(ComponentProperties.COMMENTS, "");
		form.put(ComponentProperties.JSON_DATA, "{");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SAVE_AND_RUN);
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(form).session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Components
						.submitCreated(studyClone.getId()).url());
		Result result = route(request);

		assertThat(contentAsString(result)).contains(
				"Problems deserializing JSON data string: invalid JSON format");

		removeStudy(studyClone);
	}

	@Test
	public void callChangeProperty() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Components
						.changeProperty(studyClone.getId(),
								studyClone.getComponent(1).getId(), true)
								.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callCloneComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(
						controllers.gui.routes.Components
								.cloneComponent(studyClone.getId(),
										studyClone.getComponent(1).getId())
								.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		RequestBuilder request = new RequestBuilder().method("DELETE")
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(
						controllers.gui.routes.Components
								.remove(studyClone.getId(),
										studyClone.getComponent(1).getId())
								.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);

		// Clean up - can't remove study due to some RollbackException. No idea
		// why. At least remove study assets dir.
		// publixremoveStudy(studyClone);
		ioUtils.removeStudyAssetsDir(studyClone.getDirName());
	}

}
