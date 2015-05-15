package gui.services;

import static org.fest.assertions.Assertions.assertThat;
import exceptions.BadRequestException;
import gui.AbstractGuiTest;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import models.ComponentModel;
import models.StudyModel;

import org.fest.assertions.Fail;
import org.junit.Test;

import services.gui.ComponentService;
import services.gui.MessagesStrings;
import utils.IOUtils;

import common.Global;

/**
 * Tests ComponentService
 * 
 * @author Kristian Lange
 */
public class ComponentServiceTest extends AbstractGuiTest {

	private ComponentService componentService;

	@Override
	public void before() throws Exception {
		componentService = Global.INJECTOR.getInstance(ComponentService.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void checkUpdateComponentAfterEdit()
			throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		ComponentModel component = study.getFirstComponent();
		ComponentModel clone = componentService.cloneComponentModel(component);
		clone.setActive(false);
		clone.setComments("Changed comments");
		clone.setHtmlFilePath("changed path");
		clone.setJsonData("{}");
		clone.setReloadable(false);
		clone.setTitle("Changed title");
		clone.setUuid("UUID should never be changed");
		clone.setStudy(null);
		clone.setId(0l);

		componentService.updateComponentAfterEdit(component, clone);
		ComponentModel updatedComponent = componentDao.findByUuid(
				component.getUuid(), study);

		// Unchanged stuff
		assertThat(updatedComponent.isActive() == component.isActive())
				.isTrue();
		assertThat(updatedComponent.getId().equals(component.getId())).isTrue();
		// TODO study always null: weird!
		// assertThat(updatedComponent.getStudy().equals(study)).isTrue();
		assertThat(updatedComponent.getUuid().equals(component.getUuid()))
				.isTrue();

		// Changed stuff
		assertThat(updatedComponent.getComments()).isEqualTo(
				clone.getComments());
		assertThat(updatedComponent.getHtmlFilePath()).isEqualTo(
				clone.getHtmlFilePath());
		assertThat(updatedComponent.getJsonData()).isEqualTo("{}");
		assertThat(updatedComponent.getTitle()).isEqualTo(clone.getTitle());
		assertThat(updatedComponent.isReloadable() == clone.isReloadable())
				.isTrue();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkClone() throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		ComponentModel original = study.getFirstComponent();
		ComponentModel clone = componentService.cloneComponentModel(original);

		// Equal
		assertThat(clone.getComments()).isEqualTo(original.getComments());
		assertThat(clone.getHtmlFilePath()).isEqualTo(
				original.getHtmlFilePath());
		assertThat(clone.getJsonData()).isEqualTo(original.getJsonData());
		assertThat(clone.getTitle()).isEqualTo(original.getTitle());
		assertThat(clone.isActive()).isEqualTo(original.isActive());
		assertThat(clone.isReloadable()).isEqualTo(original.isReloadable());

		// Not equal
		assertThat(clone.getId()).isNotEqualTo(original.getId());
		assertThat(clone.getUuid()).isNotEqualTo(original.getUuid());

		// Check that cloned HTML file exists
		File clonedHtmlFile = IOUtils.getFileInStudyAssetsDir(
				study.getDirName(), clone.getHtmlFilePath());
		assertThat(clonedHtmlFile.isFile()).isTrue();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkCheckStandardForComponents()
			throws NoSuchAlgorithmException, IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);
		ComponentModel component = study.getFirstComponent();
		// Study not set automatically, weird!
		component.setStudy(study);
		;

		try {
			componentService.checkStandardForComponents(study.getId(),
					component.getId(), admin, component);
		} catch (BadRequestException e) {
			Fail.fail();
		}

		long nonExistentStudyId = 2l;
		try {
			componentService.checkStandardForComponents(nonExistentStudyId,
					component.getId(), admin, component);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.componentNotBelongToStudy(
							nonExistentStudyId, component.getId()));
		}

		component.setStudy(null);
		try {
			componentService.checkStandardForComponents(study.getId(),
					component.getId(), admin, component);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.componentHasNoStudy(component.getId()));
		}

		component = null;
		try {
			componentService.checkStandardForComponents(study.getId(), null,
					admin, component);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.componentNotExist(null));
		}

		// Clean-up
		removeStudy(study);
	}
}
