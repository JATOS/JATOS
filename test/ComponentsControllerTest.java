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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;

import org.apache.http.HttpHeaders;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import play.db.jpa.JPA;
import play.mvc.Result;
import play.test.FakeRequest;
import services.Breadcrumbs;
import utils.IOUtils;

import com.google.inject.Guice;
import com.google.inject.Injector;

import controllers.Components;
import controllers.Users;
import controllers.publix.jatos.JatosPublix;

/**
 * Testing actions of controller.Components.
 * 
 * @author Kristian Lange
 */
public class ComponentsControllerTest {

	private static ControllerTestUtils utils;
	private static StudyModel studyTemplate;

	@BeforeClass
	public static void startApp() throws Exception {
		Injector injector = Guice.createInjector();
		utils = injector.getInstance(ControllerTestUtils.class);
		utils.startApp();
		studyTemplate = utils.importExampleStudy();
	}

	@AfterClass
	public static void stopApp() throws IOException {
		IOUtils.removeStudyAssetsDir(studyTemplate.getDirName());
		utils.stopApp();
	}

	/**
	 * Checks action with route Components.showComponent.
	 */
	@Test
	public void callShowComponent() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Components.showComponent(
						studyClone.getId(), studyClone.getComponent(1).getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertEquals(SEE_OTHER, status(result));
		assertThat(session(result).containsKey(JatosPublix.JATOS_SHOW));
		assertThat(session(result).containsValue(
				JatosPublix.SHOW_COMPONENT_START));
		assertThat(session(result).containsKey(JatosPublix.SHOW_COMPONENT_ID));
		assertThat(session(result).containsValue(studyClone.getId().toString()));
		assertThat(headers(result).get(HttpHeaders.LOCATION).contains(
				JatosPublix.JATOS_WORKER_ID));

		// Clean up
		utils.removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.showComponent if no html file is set
	 * within the Component.
	 */
	@Test
	public void callShowComponentNoHtml() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		JPA.em().getTransaction().begin();
		studyClone.getComponent(1).setHtmlFilePath(null);
		JPA.em().getTransaction().commit();

		Result result = callAction(
				controllers.routes.ref.Components.showComponent(
						studyClone.getId(), studyClone.getComponent(1).getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertThat(contentAsString(result))
				.contains("HTML file path is empty.");

		utils.removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.create.
	 */
	@Test
	public void callCreate() throws IOException {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Components.create(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentAsString(result)).contains(Breadcrumbs.NEW_COMPONENT);

		// Clean up
		utils.removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.submit. After the call the study's
	 * page should be shown.
	 */
	@Test
	public void callSubmit() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentModel.TITLE, "Title Test");
		form.put(ComponentModel.RELOADABLE, "true");
		form.put(ComponentModel.HTML_FILE_PATH, "html_file_path_test.html");
		form.put(ComponentModel.COMMENTS, "Comments test test.");
		form.put(ComponentModel.JSON_DATA, "{}");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SUBMIT);
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				utils.admin.getEmail()).withFormUrlEncodedBody(form);

		Result result = callAction(
				controllers.routes.ref.Components.submit(studyClone.getId()),
				request);
		assertEquals(SEE_OTHER, status(result));

		// Clean up
		utils.removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.submit. After the call the component
	 * itself should be shown.
	 */
	@Test
	public void callSubmitAndShow() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentModel.TITLE, "Title Test");
		form.put(ComponentModel.RELOADABLE, "true");
		form.put(ComponentModel.HTML_FILE_PATH, "html_file_path_test.html");
		form.put(ComponentModel.COMMENTS, "Comments test test.");
		form.put(ComponentModel.JSON_DATA, "{}");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SUBMIT_AND_SHOW);
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				utils.admin.getEmail()).withFormUrlEncodedBody(form);
		Result result = callAction(
				controllers.routes.ref.Components.submit(studyClone.getId()),
				request);
		assertEquals(SEE_OTHER, status(result));
		assertThat(headers(result).get(HttpHeaders.LOCATION)).contains("show");

		// Clean up
		utils.removeStudy(studyClone);
	}

	/**
	 * Checks action with route Components.submit with validation error.
	 */
	@Test
	public void callSubmitValidationError() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentModel.TITLE, "");
		form.put(ComponentModel.RELOADABLE, "true");
		form.put(ComponentModel.JSON_DATA, "{");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SUBMIT_AND_SHOW);
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				utils.admin.getEmail()).withFormUrlEncodedBody(form);
		Result result = callAction(
				controllers.routes.ref.Components.submit(studyClone.getId()),
				request);
		assertThat(contentAsString(result)).contains(
				"Problems deserializing JSON data string: invalid JSON format");

		utils.removeStudy(studyClone);
	}

	@Test
	public void callChangeProperties() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				utils.admin.getEmail());
		Result result = callAction(
				controllers.routes.ref.Components.changeProperty(
						studyClone.getId(), studyClone.getComponent(1).getId(),
						true), request);
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void callCloneComponent() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				utils.admin.getEmail());
		Result result = callAction(
				controllers.routes.ref.Components.cloneComponent(
						studyClone.getId(), studyClone.getComponent(1).getId()),
				request);
		assertThat(status(result)).isEqualTo(SEE_OTHER);

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void callRemove() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				utils.admin.getEmail());
		Result result = callAction(controllers.routes.ref.Components.remove(
				studyClone.getId(), studyClone.getComponent(1).getId()),
				request);
		assertThat(status(result)).isEqualTo(OK);

		// Clean up - can't remove study due to some RollbackException. No idea
		// why. At least remove study assets dir.
		// publixUtils.removeStudy(studyClone);
		IOUtils.removeStudyAssetsDir(studyClone.getDirName());
	}

}
