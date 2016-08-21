package controllers.gui;

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

import com.fasterxml.jackson.databind.JsonNode;

import controllers.gui.Components;
import controllers.gui.Users;
import general.AbstractTest;
import general.common.MessagesStrings;
import models.common.Study;
import models.gui.ComponentProperties;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import utils.common.JsonUtils;

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
								.runComponent(studyClone.getId(),
										studyClone.getComponent(1).getId(), -1l)
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
								.runComponent(studyClone.getId(),
										studyClone.getComponent(1).getId(), -1l)
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

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.charset()).isEqualTo("utf-8");
		assertThat(result.contentType()).isEqualTo("application/json");

		// Check properties in JSON
		JsonNode node = JsonUtils.OBJECTMAPPER
				.readTree(contentAsString(result));
		assertThat(node.get(ComponentProperties.TITLE).toString()).isEqualTo(
				"\"" + studyClone.getFirstComponent().getTitle() + "\"");
		assertThat(node.get(ComponentProperties.ACTIVE).toString()).isEqualTo(
				String.valueOf(studyClone.getFirstComponent().isActive()));
		assertThat(node.get(ComponentProperties.COMMENTS).toString()).isEqualTo(
				"\"" + studyClone.getFirstComponent().getComments() + "\"");
		assertThat(node.get(ComponentProperties.HTML_FILE_PATH).toString())
				.isEqualTo(
						"\"" + studyClone.getFirstComponent().getHtmlFilePath()
								+ "\"");
		assertThat(node.get(ComponentProperties.JSON_DATA).toString()).contains(
				"This component displays text and reacts to key presses.");
		assertThat(node.get(ComponentProperties.RELOADABLE).toString())
				.isEqualTo(String.valueOf(
						studyClone.getFirstComponent().isReloadable()));
		assertThat(node.get(ComponentProperties.UUID).toString()).isEqualTo(
				"\"" + studyClone.getFirstComponent().getUuid() + "\"");
		assertThat(node.get(ComponentProperties.ID).toString()).isEqualTo(
				String.valueOf(studyClone.getFirstComponent().getId()));
		assertThat(node.get("studyId").toString()).isEqualTo(String
				.valueOf(studyClone.getFirstComponent().getStudy().getId()));
		assertThat(node.get("date").toString()).isEqualTo(
				String.valueOf(studyClone.getFirstComponent().getDate()));

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
		form.put(ComponentProperties.HTML_FILE_PATH,
				"html_file_path_test.html");
		form.put(ComponentProperties.COMMENTS, "Comments test test.");
		form.put(ComponentProperties.JSON_DATA, "{}");
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(form).session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Components
						.submitCreated(studyClone.getId()).url());
		Result result = route(request);

		assertEquals(OK, result.status());

		// Clean up
		removeStudy(studyClone);
	}

	// TODO callSubmitEdited

	/**
	 * Checks action with route Components.submit. After the call the component
	 * itself should be shown.
	 */
	@Test
	public void callSubmitEdited() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentProperties.TITLE, "Title Test");
		form.put(ComponentProperties.RELOADABLE, "true");
		form.put(ComponentProperties.HTML_FILE_PATH,
				"html_file_path_test.html");
		form.put(ComponentProperties.COMMENTS, "Comments test test.");
		form.put(ComponentProperties.JSON_DATA, "{}");
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(form)
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(
						controllers.gui.routes.Components
								.submitEdited(studyClone.getId(),
										studyClone.getFirstComponent().getId())
								.url());
		Result result = route(request);

		assertEquals(OK, result.status());

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
		form.put(ComponentProperties.HTML_FILE_PATH, "%.test");
		form.put(ComponentProperties.COMMENTS, "Comments test <i>.");
		form.put(ComponentProperties.JSON_DATA, "{");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SAVE_AND_RUN);
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(form).session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Components
						.submitCreated(studyClone.getId()).url());
		Result result = route(request);

		assertThat(result.contentType()).isEqualTo("application/json");
		JsonNode node = JsonUtils.OBJECTMAPPER
				.readTree(contentAsString(result));
		assertThat(node.get(ComponentProperties.TITLE).toString())
				.isEqualTo("[\"" + MessagesStrings.MISSING_TITLE + "\"]");
		assertThat(node.get(ComponentProperties.HTML_FILE_PATH).toString())
				.isEqualTo(
						"[\"" + MessagesStrings.NOT_A_VALID_PATH_YOU_CAN_LEAVE_IT_EMPTY
								+ "\"]");
		assertThat(node.get(ComponentProperties.COMMENTS).toString())
				.isEqualTo("[\"" + MessagesStrings.NO_HTML_ALLOWED + "\"]");
		assertThat(node.get(ComponentProperties.JSON_DATA).toString())
				.isEqualTo("[\"" + MessagesStrings.INVALID_JSON_FORMAT + "\"]");

		removeStudy(studyClone);
	}

	@Test
	public void callChangeProperty() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Components
						.toggleActive(studyClone.getId(),
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
