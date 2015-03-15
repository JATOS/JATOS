package gui.services;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import gui.AbstractGuiTest;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;

import org.junit.Test;

import play.api.mvc.RequestHeader;
import play.db.jpa.JPA;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import services.gui.ImportExportService;
import utils.JsonUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import common.Global;

/**
 * Tests ImportExportService
 * 
 * @author Kristian Lange
 */
public class ImportExportServiceTest extends AbstractGuiTest {

	private ImportExportService importExportService;

	@Override
	public void before() throws Exception {
		importExportService = Global.INJECTOR
				.getInstance(ImportExportService.class);
		mockContext();
		// Don't know why, but we have to bind entityManager again
		JPA.bindForCurrentThread(entityManager);
	}

	private void mockContext() {
		Map<String, String> flashData = Collections.emptyMap();
		Map<String, Object> argData = Collections.emptyMap();
		Long id = 2L;
		RequestHeader header = mock(RequestHeader.class);
		Http.Request request = mock(Http.Request.class);
		Http.Context context = new Http.Context(id, header, request, flashData,
				flashData, argData);
		Http.Context.current.set(context);
	}

	@Override
	public void after() throws Exception {
		JPA.bindForCurrentThread(null);
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void importExistingComponent() throws Exception {
		StudyModel study = importExampleStudy();
		addStudy(study);

		// First component of the study is the one in the component file
		File componentFile = getExampleComponentFile();
		FilePart filePart = new FilePart(ComponentModel.COMPONENT,
				componentFile.getName(), "multipart/form-data", componentFile);

		// Call importComponent()
		ObjectNode jsonNode = importExportService.importComponent(study,
				filePart);
		assertThat(
				jsonNode.get(ImportExportService.COMPONENT_EXISTS).asBoolean())
				.isTrue();
		assertThat(jsonNode.get(ImportExportService.COMPONENT_TITLE).asText())
				.isEqualTo("Hello World");

		// Change properties of first component, so we have something to check
		// later on
		ComponentModel firstComponent = study.getFirstComponent();
		firstComponent = JsonUtils.initializeAndUnproxy(firstComponent);
		firstComponent.setTitle("Changed Title");
		firstComponent.setActive(false);
		firstComponent.setComments("Changed comments");
		firstComponent.setHtmlFilePath("changedHtmlFilePath");
		firstComponent.setJsonData("{}");
		firstComponent.setReloadable(false);
		// We have to set the study again otherwise it's null. Don't know why.
		firstComponent.setStudy(study);
		entityManager.getTransaction().begin();
		componentDao.update(firstComponent);
		entityManager.getTransaction().commit();

		// Call importComponentConfirmed(): Since the imported component is
		// already part of the study (at first position), it will be overwritten
		entityManager.getTransaction().begin();
		importExportService.importComponentConfirmed(study,
				componentFile.getName());
		entityManager.getTransaction().commit();

		// Check that everything in the first component was updated
		ComponentModel updatedComponent = study.getFirstComponent();
		assertThat(updatedComponent.getTitle()).isEqualTo("Hello World");
		assertThat(updatedComponent.getComments()).isEqualTo(
				"This is the most basic component.");
		assertThat(updatedComponent.getHtmlFilePath()).isEqualTo(
				"hello_world.html");
		assertThat(updatedComponent.getJsonData()).isEqualTo(null);
		assertThat(updatedComponent.getStudy()).isEqualTo(study);
		assertThat(updatedComponent.getTitle()).isEqualTo("Hello World");
		assertThat(updatedComponent.isActive()).isTrue();
		assertThat(updatedComponent.isReloadable()).isTrue();

		// IDs are unchanged
		assertThat(updatedComponent.getId()).isEqualTo(firstComponent.getId());
		assertThat(updatedComponent.getUuid()).isEqualTo(
				firstComponent.getUuid());

		// Clean-up
		if (componentFile.exists()) {
			componentFile.delete();
		}
		removeStudy(study);
	}

	@Test
	public void importNewComponent() throws Exception {
		StudyModel study = importExampleStudy();
		addStudy(study);
		File componentFile = getExampleComponentFile();
		FilePart filePart = new FilePart(ComponentModel.COMPONENT,
				componentFile.getName(), "multipart/form-data", componentFile);

		// Since the first component of the study is the one in the component
		// file, remove it
		entityManager.getTransaction().begin();
		componentDao.remove(study, study.getFirstComponent());
		entityManager.getTransaction().commit();

		// Call importComponent()
		ObjectNode jsonNode = importExportService.importComponent(study,
				filePart);
		assertThat(
				jsonNode.get(ImportExportService.COMPONENT_EXISTS).asBoolean())
				.isFalse();
		assertThat(jsonNode.get(ImportExportService.COMPONENT_TITLE).asText())
				.isEqualTo("Hello World");

		// Call importComponentConfirmed(): The new component will be put on the
		// end of study's component list
		entityManager.getTransaction().begin();
		importExportService.importComponentConfirmed(study,
				componentFile.getName());
		entityManager.getTransaction().commit();

		// Check all properties of the last component
		ComponentModel newComponent = study.getLastComponent();
		assertThat(newComponent.getTitle()).isEqualTo("Hello World");
		assertThat(newComponent.getComments()).isEqualTo(
				"This is the most basic component.");
		assertThat(newComponent.getHtmlFilePath())
				.isEqualTo("hello_world.html");
		assertThat(newComponent.getJsonData()).isEqualTo(null);
		assertThat(newComponent.getStudy()).isEqualTo(study);
		assertThat(newComponent.getTitle()).isEqualTo("Hello World");
		assertThat(newComponent.isActive()).isTrue();
		assertThat(newComponent.isReloadable()).isTrue();
		assertThat(newComponent.getId()).isNotNull();
		assertThat(newComponent.getUuid()).isEqualTo(
				"ae05b118-7d9a-4e5b-bd6c-8109d42e371e");

		// Clean-up
		if (componentFile.exists()) {
			componentFile.delete();
		}
		removeStudy(study);
	}

	@Test
	public void importNewStudy() throws Exception {
		File studyFile = getExampleStudyFile();
		FilePart filePart = new FilePart(StudyModel.STUDY, studyFile.getName(),
				"multipart/form-data", studyFile);
		ObjectNode jsonNode = importExportService.importStudy(admin, filePart);
		assertThat(jsonNode.get(ImportExportService.STUDY_EXISTS).asBoolean())
				.isFalse();
		assertThat(jsonNode.get(ImportExportService.STUDY_TITLE).asText())
				.isEqualTo("Basic Example Study");
		assertThat(jsonNode.get(ImportExportService.DIR_EXISTS).asBoolean())
				.isFalse();
		// assertThat(jsonNode.get(ImportExportService.DIR_PATH).asText())
		// .isEqualTo(studyFile.getName());
	}

}
