package gui.services;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;
import gui.AbstractGuiTest;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;

import org.h2.tools.Server;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.junit.Test;

import play.Logger;
import play.api.mvc.RequestHeader;
import play.db.jpa.JPA;
import play.db.jpa.Transactional;
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
		importExportService = Global.INJECTOR
				.getInstance(ImportExportService.class);

		Map<String, String> flashData = Collections.emptyMap();
		Map<String, Object> argData = Collections.emptyMap();
		Long id = 2L;
		RequestHeader header = mock(RequestHeader.class);
		Http.Request request = mock(Http.Request.class);
		Http.Context context = new Http.Context(id, header, request, flashData,
				flashData, argData);
		Http.Context.current.set(context);
		JPA.bindForCurrentThread(entityManager);
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
	
	@SuppressWarnings("unchecked")
	public <T> T initializeAndUnproxy(T obj) {
		Hibernate.initialize(obj);
		if (obj instanceof HibernateProxy) {
			obj = (T) ((HibernateProxy) obj).getHibernateLazyInitializer()
					.getImplementation();
		}
		return obj;
	}

	/**
	 * Import a component that already exists in a study. It should be
	 * overwritten.
	 * 
	 * @throws Exception
	 */
	@Test
	public void importExistingComponent() throws Exception {
		Server server = Server.createTcpServer().start();
		System.out.println("URL: jdbc:h2:" + server.getURL() + "/mem:test/jatos");
		
		StudyModel studyTemplate = importExampleStudy();
		StudyModel study = cloneAndPersistStudy(studyTemplate);
		study = studyDao.findById(study.getId());

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
		
		StudyModel studyOfFirstComponent = initializeAndUnproxy(initializeAndUnproxy(study).getFirstComponent()).getStudy();
		Logger.info(study.getFirstComponent().getStudy().toString());
		
		// Change properties of first component, so we have something to check
		ComponentModel firstComponent = initializeAndUnproxy(componentDao.findById(study.getFirstComponent().getId()));
		ComponentModel updatedComponent = new ComponentModel(firstComponent);
		updatedComponent.setTitle("Changed Title");
		entityManager.getTransaction().begin();
		componentDao.updateProperties(firstComponent, updatedComponent);
		entityManager.getTransaction().commit();

		// Call importComponentConfirmed(): Since the imported component is
		// already part of the study (at first position), it will be overwritten
		entityManager.getTransaction().begin();
		importExportService.importComponentConfirmed(study,
				componentFile.getName());
		entityManager.getTransaction().commit();

		study = studyDao.findById(study.getId());
		updatedComponent = study.getFirstComponent();
		assertThat(updatedComponent.getTitle()).isEqualTo("Hello World");

		// Clean-up
		if (componentFile.exists()) {
			componentFile.delete();
		}
		removeStudy(study);
		server.stop();
	}

	// @Test
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

	// @Test
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
		assertThat(jsonNode.get(ImportExportService.DIR_PATH).asText())
				.isEqualTo(studyFile.getName());
	}

}
