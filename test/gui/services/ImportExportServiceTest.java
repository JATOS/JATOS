package gui.services;

import static org.fest.assertions.Assertions.assertThat;
import gui.AbstractGuiTest;

import java.io.File;

import models.ComponentModel;
import models.StudyModel;

import org.junit.Test;

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
		importExportService = Global.INJECTOR
				.getInstance(ImportExportService.class);
	}

	@Override
	public void after() throws Exception {
		// Nothing additional to AbstractGuiTest to to do after test
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void importComponent() throws Exception {
		StudyModel study = importExampleStudy();
		addStudy(study);
		File componentFile = getExampleComponentFile();
		FilePart filePart = new FilePart(ComponentModel.COMPONENT,
				componentFile.getName(), "multipart/form-data", componentFile);
		ObjectNode objectNode = importExportService.importComponent(study,
				filePart);
		// First component of study is the imported component
		assertThat(objectNode.get(ImportExportService.COMPONENT_EXISTS)
				.asBoolean());
		assertThat(objectNode.get(ImportExportService.COMPONENT_TITLE).asText())
				.isEqualTo("Hello World");

		// Remove first component of study
		entityManager.getTransaction().begin();
		study.removeComponent(study.getFirstComponent());
		entityManager.getTransaction().commit();
		objectNode = importExportService.importComponent(study, filePart);
		assertThat(
				objectNode.get(ImportExportService.COMPONENT_EXISTS)
						.asBoolean()).isFalse();

		// Clean-up
		if (componentFile.exists()) {
			componentFile.delete();
		}
		removeStudy(study);
	}

}
