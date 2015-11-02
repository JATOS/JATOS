package gui.services;

import static org.fest.assertions.Assertions.assertThat;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import common.AbstractTest;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import models.Component;
import models.Study;
import models.workers.PersonalSingleWorker;
import models.workers.JatosWorker;
import models.workers.PersonalMultipleWorker;

import org.junit.Test;

import play.db.jpa.JPA;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import services.ImportExportService;
import utils.IOUtils;
import utils.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import common.Global;

/**
 * Tests ImportExportService
 * 
 * @author Kristian Lange
 */
public class ImportExportServiceTest extends AbstractTest {

	private ImportExportService importExportService;

	@Override
	public void before() throws Exception {
		importExportService = Global.INJECTOR
				.getInstance(ImportExportService.class);
		mockContext();
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

	/**
	 * Import a component that already exists in a study. It should be
	 * overwritten.
	 * 
	 * @throws Exception
	 */
	@Test
	public void importExistingComponent() throws Exception {
		Study study = importExampleStudy();
		addStudy(study);

		// First component of the study is the one in the component file
		File componentFile = getExampleComponentFile();
		FilePart filePart = new FilePart(Component.COMPONENT,
				componentFile.getName(), "multipart/form-data", componentFile);

		// Call importComponent()
		ObjectNode jsonNode = importExportService.importComponent(study,
				filePart);
		assertThat(
				jsonNode.get(ImportExportService.COMPONENT_EXISTS).asBoolean())
				.isTrue();
		assertThat(jsonNode.get(ImportExportService.COMPONENT_TITLE).asText())
				.isEqualTo("Quit button");

		// Change properties of first component, so we have something to check
		// later on
		Component firstComponent = study.getFirstComponent();
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
		Component updatedComponent = study.getFirstComponent();

		// Check that IDs are unchanged
		assertThat(updatedComponent.getId()).isEqualTo(firstComponent.getId());
		assertThat(updatedComponent.getUuid()).isEqualTo(
				firstComponent.getUuid());

		// Check changed component properties
		assertThat(updatedComponent.getTitle()).isEqualTo("Changed Title");
		assertThat(updatedComponent.getComments())
				.isEqualTo("Changed comments");
		assertThat(updatedComponent.getHtmlFilePath()).isEqualTo(
				"changedHtmlFilePath");
		assertThat(updatedComponent.getJsonData()).isEqualTo("{}");
		assertThat(updatedComponent.getStudy()).isEqualTo(study);
		assertThat(updatedComponent.isActive()).isFalse();
		assertThat(updatedComponent.isReloadable()).isFalse();

		// Clean-up
		if (componentFile.exists()) {
			componentFile.delete();
		}
		removeStudy(study);
	}

	@Test
	public void importNewComponent() throws NoSuchAlgorithmException,
			IOException {
		Study study = importExampleStudy();
		addStudy(study);
		File componentFile = getExampleComponentFile();
		FilePart filePart = new FilePart(Component.COMPONENT,
				componentFile.getName(), "multipart/form-data", componentFile);

		// Remove the last component (so we can import it again later on)
		entityManager.getTransaction().begin();
		componentDao.remove(study, study.getLastComponent());
		entityManager.getTransaction().commit();

		// Check that the last component is removed
		assertThat(study.getLastComponent().getTitle()).isNotEqualTo(
				"Quit button");

		// Import 1. part: Call importComponent()
		ObjectNode jsonNode = importExportService.importComponent(study,
				filePart);

		// Check returned JSON object
		assertThat(
				jsonNode.get(ImportExportService.COMPONENT_EXISTS).asBoolean())
				.isFalse();
		assertThat(jsonNode.get(ImportExportService.COMPONENT_TITLE).asText())
				.isEqualTo("Quit button");

		// Import 2. part: Call importComponentConfirmed(): The new component
		// will be
		// put on the end of study's component list
		entityManager.getTransaction().begin();
		importExportService.importComponentConfirmed(study,
				componentFile.getName());
		entityManager.getTransaction().commit();

		// Check all properties of the imported component
		Component importedComponent = study.getLastComponent();
		assertThat(study.getLastComponent().getTitle())
				.isEqualTo("Quit button");
		assertThat(importedComponent.getId()).isNotNull();
		assertThat(importedComponent.getUuid()).isEqualTo(
				"503941c3-a0d5-43dc-ae56-083ab08df4b2");
		assertThat(importedComponent.getComments()).isEqualTo("");
		assertThat(importedComponent.getHtmlFilePath()).isEqualTo(
				"quit_button.html");
		assertThat(importedComponent.getJsonData()).contains(
				"This component is about what you can do in the client side");
		assertThat(importedComponent.getStudy()).isEqualTo(study);
		assertThat(importedComponent.isActive()).isTrue();
		assertThat(importedComponent.isReloadable()).isFalse();

		// Clean-up
		if (componentFile.exists()) {
			componentFile.delete();
		}
		removeStudy(study);
	}

	@Test
	public void importNewStudy() throws IOException, ForbiddenException,
			BadRequestException {

		// Import 1. part: Call importStudy()
		File studyFile = getExampleStudyFile();
		FilePart filePart = new FilePart(Study.STUDY, studyFile.getName(),
				"multipart/form-data", studyFile);
		ObjectNode jsonNode = importExportService.importStudy(admin,
				filePart.getFile());

		// Check returned JSON object
		assertThat(jsonNode.get(ImportExportService.STUDY_EXISTS).asBoolean())
				.isFalse();
		assertThat(jsonNode.get(ImportExportService.STUDY_TITLE).asText())
				.isEqualTo("Basic Example Study");
		assertThat(jsonNode.get(ImportExportService.DIR_EXISTS).asBoolean())
				.isFalse();
		assertThat(
				jsonNode.get(ImportExportService.DIR_PATH).asText() + "."
						+ IOUtils.ZIP_FILE_SUFFIX).isEqualTo(
				studyFile.getName());

		// Import 2. part: Call importStudyConfirmed(): Since this study is new,
		// the overwrite parameters don't matter
		ObjectNode node = JsonUtils.OBJECTMAPPER.createObjectNode();
		node.put(ImportExportService.STUDYS_ENTITY_CONFIRM, true);
		node.put(ImportExportService.STUDYS_DIR_CONFIRM, true);
		entityManager.getTransaction().begin();
		importExportService.importStudyConfirmed(admin, node);
		entityManager.getTransaction().commit();

		// Check properties and assets of imported study
		List<Study> studyList = studyDao.findAll();
		assertThat(studyList.size() == 1).isTrue();
		Study study = studyList.get(0);
		checkPropertiesOfBasicExampleStudy(study);
		checkAssetsOfBasicExampleStudy(study, "basic_example_study");

		// Clean up
		removeStudy(study);
	}

	private void checkPropertiesOfBasicExampleStudy(Study study) {
		assertThat(study.getAllowedWorkerTypeList()).containsOnly(
				JatosWorker.WORKER_TYPE, PersonalSingleWorker.WORKER_TYPE,
				PersonalMultipleWorker.WORKER_TYPE);
		assertThat(study.getComponentList().size()).isEqualTo(7);
		assertThat(study.getComponent(1).getTitle()).isEqualTo(
				"Show JSON input ");
		assertThat(study.getLastComponent().getTitle())
				.isEqualTo("Quit button");
		assertThat(study.getDate()).isNull();
		assertThat(study.getDescription()).isEqualTo(
				"A couple of sample components.");
		assertThat(study.getId()).isPositive();
		assertThat(study.getJsonData().contains("\"totalStudySlides\":17"))
				.isTrue();
		assertThat(study.getUserList().contains(admin)).isTrue();
		assertThat(study.getTitle()).isEqualTo("Basic Example Study");
		assertThat(study.getUuid()).isEqualTo(
				"5c85bd82-0258-45c6-934a-97ecc1ad6617");
	}

	private void checkAssetsOfBasicExampleStudy(Study study, String dirName)
			throws IOException {
		assertThat(study.getDirName()).isEqualTo(dirName);
		assertThat(IOUtils.checkStudyAssetsDirExists(study.getDirName()))
				.isTrue();

		// Check the number of files and directories in the study assets
		String[] fileList = IOUtils.getStudyAssetsDir(study.getDirName())
				.list();
		assertThat(fileList.length).isEqualTo(11);
	}

	@Test
	public void importStudyOverwritePropertiesAndAssets()
			throws NoSuchAlgorithmException, IOException, ForbiddenException,
			BadRequestException {
		// Import study and alter it, so we have something to overwrite later on
		Study study = importExampleStudy();
		alterStudy(study);
		addStudy(study);

		entityManager.getTransaction().begin();
		studyService.renameStudyAssetsDir(study, "changed_dirname");
		entityManager.getTransaction().commit();

		// Import 1. call: importStudy()
		File studyFile = getExampleStudyFile();
		FilePart filePart = new FilePart(Study.STUDY, studyFile.getName(),
				"multipart/form-data", studyFile);
		ObjectNode jsonNode = importExportService.importStudy(admin,
				filePart.getFile());

		// Check returned JSON object
		assertThat(jsonNode.get(ImportExportService.STUDY_EXISTS).asBoolean())
				.isTrue();
		assertThat(jsonNode.get(ImportExportService.STUDY_TITLE).asText())
				.isEqualTo("Basic Example Study");
		assertThat(jsonNode.get(ImportExportService.DIR_EXISTS).asBoolean())
				.isFalse();
		assertThat(
				jsonNode.get(ImportExportService.DIR_PATH).asText() + "."
						+ IOUtils.ZIP_FILE_SUFFIX).isEqualTo(
				studyFile.getName());

		// Import 2. call: importStudyConfirmed(): Allow properties and assets
		// to be overwritten
		ObjectNode node = JsonUtils.OBJECTMAPPER.createObjectNode();
		node.put(ImportExportService.STUDYS_ENTITY_CONFIRM, true);
		node.put(ImportExportService.STUDYS_DIR_CONFIRM, true);
		entityManager.getTransaction().begin();
		importExportService.importStudyConfirmed(admin, node);
		entityManager.getTransaction().commit();

		// Check properties and assets of imported study
		List<Study> studyList = studyDao.findAll();
		assertThat(studyList.size() == 1).isTrue();
		Study importedStudy = studyList.get(0);
		checkPropertiesOfBasicExampleStudy(importedStudy);
		checkAssetsOfBasicExampleStudy(study, "basic_example_study");

		// Clean up
		removeStudy(study);
	}

	@Test
	public void importStudyOverwritePropertiesNotAssets()
			throws NoSuchAlgorithmException, IOException, ForbiddenException,
			BadRequestException {
		// Import study, so we have something to overwrite
		Study study = importExampleStudy();
		alterStudy(study);
		addStudy(study);

		// Change study assets dir name, so we can check it later on
		entityManager.getTransaction().begin();
		studyService.renameStudyAssetsDir(study, "original_dirname");
		entityManager.getTransaction().commit();

		// Import 1. call: importStudy()
		File studyFile = getExampleStudyFile();
		FilePart filePart = new FilePart(Study.STUDY, studyFile.getName(),
				"multipart/form-data", studyFile);
		ObjectNode jsonNode = importExportService.importStudy(admin,
				filePart.getFile());

		// Check returned JSON object
		assertThat(jsonNode.get(ImportExportService.STUDY_EXISTS).asBoolean())
				.isTrue();
		assertThat(jsonNode.get(ImportExportService.STUDY_TITLE).asText())
				.isEqualTo("Basic Example Study");
		assertThat(jsonNode.get(ImportExportService.DIR_EXISTS).asBoolean())
				.isFalse();
		assertThat(
				jsonNode.get(ImportExportService.DIR_PATH).asText() + "."
						+ IOUtils.ZIP_FILE_SUFFIX).isEqualTo(
				studyFile.getName());

		// Call importStudyConfirmed(): Allow properties but not assets to be
		// overwritten
		ObjectNode node = JsonUtils.OBJECTMAPPER.createObjectNode();
		node.put(ImportExportService.STUDYS_ENTITY_CONFIRM, true);
		node.put(ImportExportService.STUDYS_DIR_CONFIRM, false);
		entityManager.getTransaction().begin();
		importExportService.importStudyConfirmed(admin, node);
		entityManager.getTransaction().commit();

		// Check properties (overwritten) and assets (not overwritten)
		List<Study> studyList = studyDao.findAll();
		assertThat(studyList.size() == 1).isTrue();
		Study importedStudy = studyList.get(0);
		checkPropertiesOfBasicExampleStudy(importedStudy);
		checkAssetsOfBasicExampleStudy(study, "original_dirname");

		// Clean up
		removeStudy(study);
	}

	@Test
	public void importStudyOverwriteAssetsNotProperties()
			throws NoSuchAlgorithmException, IOException, ForbiddenException,
			BadRequestException {
		// Import study and alter it, so we have something to overwrite
		Study study = importExampleStudy();
		alterStudy(study);
		addStudy(study);

		// Change study assets dir name so we have something to overwrite
		entityManager.getTransaction().begin();
		studyService.renameStudyAssetsDir(study, "original_dirname");
		entityManager.getTransaction().commit();

		// Import 1. call
		File studyFile = getExampleStudyFile();
		FilePart filePart = new FilePart(Study.STUDY, studyFile.getName(),
				"multipart/form-data", studyFile);
		ObjectNode jsonNode = importExportService.importStudy(admin,
				filePart.getFile());

		// Check returned JSON object
		assertThat(jsonNode.get(ImportExportService.STUDY_EXISTS).asBoolean())
				.isTrue();
		assertThat(jsonNode.get(ImportExportService.STUDY_TITLE).asText())
				.isEqualTo("Basic Example Study");
		assertThat(jsonNode.get(ImportExportService.DIR_EXISTS).asBoolean())
				.isFalse();
		assertThat(
				jsonNode.get(ImportExportService.DIR_PATH).asText() + "."
						+ IOUtils.ZIP_FILE_SUFFIX).isEqualTo(
				studyFile.getName());

		// Import 2. call: importStudyConfirmed(): Allow assets but not
		// properties to be overwritten
		ObjectNode node = JsonUtils.OBJECTMAPPER.createObjectNode();
		node.put(ImportExportService.STUDYS_ENTITY_CONFIRM, false);
		node.put(ImportExportService.STUDYS_DIR_CONFIRM, true);
		entityManager.getTransaction().begin();
		importExportService.importStudyConfirmed(admin, node);
		entityManager.getTransaction().commit();

		// Check Properties (should not have changed) 
		assertThat(study.getAllowedWorkerTypeList()).containsOnly(
				JatosWorker.WORKER_TYPE);
		assertThat(study.getComponentList().size()).isEqualTo(6);
		assertThat(study.getComponent(1).getTitle()).isEqualTo(
				"Task instructions ");
		assertThat(study.getLastComponent().getTitle()).isEqualTo(
				"Changed title");
		assertThat(study.getDate()).isNull();
		assertThat(study.getDescription()).isEqualTo("Changed description");
		assertThat(study.getId()).isPositive();
		assertThat(study.getJsonData()).isEqualTo("{}");
		assertThat(study.getUserList().contains(admin)).isTrue();
		assertThat(study.getTitle()).isEqualTo("Changed Title");
		assertThat(study.getUuid()).isEqualTo(
				"5c85bd82-0258-45c6-934a-97ecc1ad6617");

		// Asset dir name should not have changed
		checkAssetsOfBasicExampleStudy(study, "original_dirname");

		// Clean up
		removeStudy(study);
	}

	private void alterStudy(Study study) {
		study.removeAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		study.removeAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		study.getComponentList().remove(0);
		study.getLastComponent().setTitle("Changed title");
		study.setDescription("Changed description");
		study.setJsonData("{}");
		study.setTitle("Changed Title");
	}

	@Test
	public void checkCreateStudyExportZipFile()
			throws NoSuchAlgorithmException, IOException, ForbiddenException {
		Study study = importExampleStudy();
		addStudy(study);

		// Export study into a file
		File studyFile = importExportService.createStudyExportZipFile(study);

		// Import the exported study again
		JsonNode jsonNode = importExportService.importStudy(admin, studyFile);
		
		// Check returned JSON object
		assertThat(jsonNode.get(ImportExportService.STUDY_EXISTS).asBoolean())
				.isTrue();
		assertThat(jsonNode.get(ImportExportService.STUDY_TITLE).asText())
				.isEqualTo("Basic Example Study");
		assertThat(jsonNode.get(ImportExportService.DIR_EXISTS).asBoolean())
				.isTrue();
		assertThat(jsonNode.get(ImportExportService.DIR_PATH).asText())
				.isNotEmpty();

		// importStudy() should remember the study file name in the Play session
		String studyFileName = Http.Context.current.get().session()
				.get(ImportExportService.SESSION_UNZIPPED_STUDY_DIR);
		assertThat(studyFileName).isNotEmpty();

		// Clean up
		removeStudy(study);
	}

}
