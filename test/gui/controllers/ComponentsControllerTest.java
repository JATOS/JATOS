package gui.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.callAction;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.headers;
import static play.test.Helpers.session;
import static play.test.Helpers.status;
import common.AbstractTest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import models.Component;
import models.Study;

import org.apache.http.HttpHeaders;
import org.junit.Test;

import play.mvc.Result;
import play.test.FakeRequest;
import publix.controllers.jatos.JatosPublix;
import services.BreadcrumbsService;
import utils.IOUtils;
import controllers.Components;
import controllers.Users;

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
		IOUtils.removeStudyAssetsDir(studyTemplate.getDirName());
	}

	/**
	 * Checks action with route Components.showComponent.
	 */
	@Test
	public void callShowComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(controllers.routes.ref.Components
				.showComponent(studyClone.getId(), studyClone.getComponent(1)
						.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertEquals(SEE_OTHER, status(result));
		assertThat(session(result).containsKey(JatosPublix.JATOS_RUN));
		assertThat(session(result).containsValue(
				JatosPublix.RUN_COMPONENT_START));
		assertThat(session(result).containsKey(JatosPublix.RUN_COMPONENT_ID));
		assertThat(session(result).containsValue(studyClone.getId().toString()));
		assertThat(headers(result).get(HttpHeaders.LOCATION).contains(
				JatosPublix.JATOS_WORKER_ID));

		// Clean up
		removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.showComponent if no html file is set
	 * within the Component.
	 */
	@Test
	public void callShowComponentNoHtml() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		entityManager.getTransaction().begin();
		studyClone.getComponent(1).setHtmlFilePath(null);
		entityManager.getTransaction().commit();

		Result result = callAction(controllers.routes.ref.Components
				.showComponent(studyClone.getId(), studyClone.getComponent(1)
						.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(contentAsString(result))
				.contains("HTML file path is empty.");

		removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.create.
	 */
	@Test
	public void callCreate() throws IOException {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Components
						.create(studyClone.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentAsString(result)).contains(BreadcrumbsService.NEW_COMPONENT);

		// Clean up
		removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.submit. After the call the study's
	 * page should be shown.
	 */
	@Test
	public void callSubmit() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Map<String, String> form = new HashMap<String, String>();
		form.put(Component.TITLE, "Title Test");
		form.put(Component.RELOADABLE, "true");
		form.put(Component.HTML_FILE_PATH, "html_file_path_test.html");
		form.put(Component.COMMENTS, "Comments test test.");
		form.put(Component.JSON_DATA, "{}");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SUBMIT);
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				admin.getEmail()).withFormUrlEncodedBody(form);

		Result result = callAction(
				controllers.routes.ref.Components
						.submit(studyClone.getId()),
				request);
		assertEquals(SEE_OTHER, status(result));

		// Clean up
		removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.submit. After the call the component
	 * itself should be shown.
	 */
	@Test
	public void callSubmitAndShow() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Map<String, String> form = new HashMap<String, String>();
		form.put(Component.TITLE, "Title Test");
		form.put(Component.RELOADABLE, "true");
		form.put(Component.HTML_FILE_PATH, "html_file_path_test.html");
		form.put(Component.COMMENTS, "Comments test test.");
		form.put(Component.JSON_DATA, "{}");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SUBMIT_AND_SHOW);
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				admin.getEmail()).withFormUrlEncodedBody(form);
		Result result = callAction(
				controllers.routes.ref.Components
						.submit(studyClone.getId()),
				request);
		assertEquals(SEE_OTHER, status(result));
		assertThat(headers(result).get(HttpHeaders.LOCATION)).contains("show");

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
		form.put(Component.TITLE, "");
		form.put(Component.RELOADABLE, "true");
		form.put(Component.HTML_FILE_PATH, "");
		form.put(Component.COMMENTS, "");
		form.put(Component.JSON_DATA, "{");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SUBMIT_AND_SHOW);
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				admin.getEmail()).withFormUrlEncodedBody(form);
		Result result = callAction(
				controllers.routes.ref.Components
						.submit(studyClone.getId()),
				request);
		assertThat(contentAsString(result)).contains(
				"Problems deserializing JSON data string: invalid JSON format");

		removeStudy(studyClone);
	}

	@Test
	public void callChangeProperties() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				admin.getEmail());
		Result result = callAction(
				controllers.routes.ref.Components.changeProperty(
						studyClone.getId(), studyClone.getComponent(1).getId(),
						true), request);
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callCloneComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				admin.getEmail());
		Result result = callAction(
				controllers.routes.ref.Components.cloneComponent(
						studyClone.getId(), studyClone.getComponent(1).getId()),
				request);
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				admin.getEmail());
		Result result = callAction(
				controllers.routes.ref.Components.remove(
						studyClone.getId(), studyClone.getComponent(1).getId()),
				request);
		assertThat(status(result)).isEqualTo(OK);

		// Clean up - can't remove study due to some RollbackException. No idea
		// why. At least remove study assets dir.
		// publixremoveStudy(studyClone);
		IOUtils.removeStudyAssetsDir(studyClone.getDirName());
	}

}
