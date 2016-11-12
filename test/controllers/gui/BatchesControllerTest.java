package controllers.gui;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import general.AbstractTest;
import models.common.Study;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import utils.common.JsonUtils;

/**
 * Testing actions of controller.Batches.
 * 
 * @author Kristian Lange
 */
public class BatchesControllerTest extends AbstractTest {

	private static Study studyTemplate;

	@Override
	public void before() throws Exception {
		studyTemplate = importExampleStudy();
	}

	@Override
	public void after() throws Exception {
		ioUtils.removeStudyAssetsDir(studyTemplate.getDirName());
	}

	@Test
	public void callCreatePersonalSingleRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		JsonNode jsonNode = JsonUtils.OBJECTMAPPER
				.readTree("{\"comment\": \"test comment\",\"amount\": 10}");
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyJson(jsonNode)
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(
						controllers.gui.routes.Batches
								.createPersonalSingleRun(studyClone.getId(),
										studyClone.getDefaultBatch().getId())
								.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.contentType()).isEqualTo("application/json");
		jsonNode = JsonUtils.OBJECTMAPPER.readTree(contentAsString(result));
		assertThat(jsonNode.isArray()).isTrue();
		assertThat(jsonNode.size()).isEqualTo(10);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callCreatePersonalMultipleRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		JsonNode jsonNode = JsonUtils.OBJECTMAPPER
				.readTree("{\"comment\": \"test comment\",\"amount\": 10}");
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyJson(jsonNode)
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(
						controllers.gui.routes.Batches
								.createPersonalMultipleRun(studyClone.getId(),
										studyClone.getDefaultBatch().getId())
								.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.contentType()).isEqualTo("application/json");
		jsonNode = JsonUtils.OBJECTMAPPER.readTree(contentAsString(result));
		assertThat(jsonNode.isArray()).isTrue();
		assertThat(jsonNode.size()).isEqualTo(10);

		// Clean up
		removeStudy(studyClone);
	}

}
