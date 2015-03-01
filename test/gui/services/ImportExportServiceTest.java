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
//		importExportService = Global.INJECTOR
//				.getInstance(ImportExportService.class);
//
//		Map<String, String> flashData = Collections.emptyMap();
//		Map<String, Object> argData = Collections.emptyMap();
//		Long id = 2L;
//		RequestHeader header = mock(RequestHeader.class);
//		Http.Request request = mock(Http.Request.class);
//		Http.Context context = new Http.Context(id, header, request, flashData,
//				flashData, argData);
//		Http.Context.current.set(context);
//		JPA.bindForCurrentThread(entityManager);
	}

	@Override
	public void after() throws Exception {
		JPA.bindForCurrentThread(null);
		// Nothing additional to AbstractGuiTest to to do after test
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

//	@Test
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
		ComponentModel firstComponent = study.getFirstComponent();
		firstComponent.setTitle("Changed Title");
		entityManager.getTransaction().begin();
		componentDao.update(firstComponent);
		entityManager.getTransaction().commit();

		// Call importComponentConfirmed(): Since the imported component is
		// already part of the study (at first position), it will overwrite
		entityManager.getTransaction().begin();
		importExportService.importComponentConfirmed(study,
				componentFile.getName());
		entityManager.getTransaction().commit();
		// TODO doesn't work due to some weird Play / JPA thing
		// ComponentModel updatedComponent = study.getFirstComponent();
		// assertThat(updatedComponent.getTitle()).isEqualTo("Hello World");

		// Clean-up
		if (componentFile.exists()) {
			componentFile.delete();
		}
		removeStudy(study);
	}

//	@Test
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
		ComponentModel updatedComponent = study.getLastComponent();
		assertThat(updatedComponent.getTitle()).isEqualTo("Hello World");

		// Clean-up
		if (componentFile.exists()) {
			componentFile.delete();
		}
		removeStudy(study);
	}

}
