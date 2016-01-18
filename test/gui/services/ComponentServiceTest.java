package gui.services;

import static org.fest.assertions.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.gui.BadRequestException;
import general.AbstractTest;
import general.common.MessagesStrings;
import models.common.Component;
import models.common.Study;
import models.gui.ComponentProperties;

/**
 * Tests ComponentService
 * 
 * @author Kristian Lange
 */
public class ComponentServiceTest extends AbstractTest {

	@Override
	public void before() throws Exception {
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
		Study study = importExampleStudy();
		addStudy(study);

		Component component = study.getFirstComponent();
		ComponentProperties updatedProps = new ComponentProperties();
		updatedProps.setActive(false);
		updatedProps.setComments("Changed comments");
		updatedProps.setHtmlFilePath("changed path");
		updatedProps.setJsonData("{}");
		updatedProps.setReloadable(false);
		updatedProps.setTitle("Changed title");
		updatedProps.setUuid("UUID should never be changed");
		updatedProps.setStudyId(1234l);
		updatedProps.setId(4321l);

		componentService.updateComponentAfterEdit(component, updatedProps);
		Component updatedComponent = componentDao
				.findByUuid(component.getUuid(), study);

		// Unchanged stuff
		assertThat(updatedComponent.isActive() == component.isActive())
				.isTrue();
		assertThat(updatedComponent.getId().equals(component.getId())).isTrue();
		// TODO study always null: weird!
		// TODO we shouldn'T be able to overwrite IDs
		// assertThat(updatedComponent.getStudy().equals(study)).isTrue();
		assertThat(updatedComponent.getUuid().equals(component.getUuid()))
				.isTrue();
		assertThat(updatedComponent.getHtmlFilePath())
				.isEqualTo(component.getHtmlFilePath());
		// TODO check study ID
		
		// Changed stuff
		assertThat(updatedComponent.getComments())
				.isEqualTo(updatedProps.getComments());
		assertThat(updatedComponent.getJsonData()).isEqualTo("{}");
		assertThat(updatedComponent.getTitle()).isEqualTo(updatedProps.getTitle());
		assertThat(updatedComponent.isReloadable() == updatedProps.isReloadable())
				.isTrue();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRenameHtmlFilePath()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		Component component = study.getFirstComponent();
		// Study not set automatically, weird!
		component.setStudy(study);

		File htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
				component.getHtmlFilePath());
		assertThat(htmlFile.exists());

		// Check standard renaming
		componentService.renameHtmlFilePath(component, "foo.html");
		htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
				"foo.html");
		assertThat(component.getHtmlFilePath()).isEqualTo("foo.html");
		assertThat(htmlFile.exists());

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRenameHtmlFilePathNewFileExists()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		Component component = study.getFirstComponent();
		// Study not set automatically, weird!
		component.setStudy(study);

		File htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
				component.getHtmlFilePath());
		assertThat(htmlFile.exists());

		// Try renaming to existing file
		try {
			componentService.renameHtmlFilePath(component,
					study.getLastComponent().getHtmlFilePath());
			Fail.fail();
		} catch (IOException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.htmlFileNotRenamedBecauseExists(
							component.getHtmlFilePath(),
							study.getLastComponent().getHtmlFilePath()));
		}

		// Everything is unchanged
		assertThat(component.getHtmlFilePath()).isEqualTo(htmlFile.getName());
		assertThat(htmlFile.exists());

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRenameHtmlFilePathWithSubFolder()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		Component component = study.getFirstComponent();
		// Study not set automatically, weird!
		component.setStudy(study);

		File htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
				component.getHtmlFilePath());
		assertThat(htmlFile.exists());

		// Create subfolder
		File subfolder = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
				"subfolder");
		subfolder.mkdir();
		assertThat(subfolder.exists());

		// Check renaming into a subfolder
		componentService.renameHtmlFilePath(component, "subfolder/foo.html");
		htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
				"subfolder/foo.html");
		assertThat(component.getHtmlFilePath()).isEqualTo("subfolder/foo.html");
		assertThat(htmlFile.exists());
		assertThat(htmlFile.getParentFile().getName()).isEqualTo("subfolder");

		// Check renaming back into study assets
		componentService.renameHtmlFilePath(component, "foo.html");
		htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
				"foo.html");
		assertThat(component.getHtmlFilePath()).isEqualTo("foo.html");
		assertThat(htmlFile.exists());

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRenameHtmlFilePathEmptyNewFile()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		Component component = study.getFirstComponent();
		// Study not set automatically, weird!
		component.setStudy(study);

		File htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
				component.getHtmlFilePath());
		assertThat(htmlFile.exists());

		// If new filename is empty leave the file alone and put "" into the db
		componentService.renameHtmlFilePath(component, "");
		assertThat(component.getHtmlFilePath()).isEqualTo("");
		assertThat(htmlFile.exists());

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRenameHtmlFilePathCurrentFileNotExistNewFileNotExist()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		Component component = study.getFirstComponent();
		// Study not set automatically, weird!
		component.setStudy(study);

		File htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
				component.getHtmlFilePath());
		assertThat(htmlFile.exists());

		// Remove current HTML file
		htmlFile.delete();
		assertThat(!htmlFile.exists());

		// Rename to non-existing file - Current file doesn't exist - new file
		// name must be set and file still not existing
		componentService.renameHtmlFilePath(component, "foo.html");
		htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
				"foo.html");
		assertThat(component.getHtmlFilePath()).isEqualTo("foo.html");
		assertThat(!htmlFile.exists());

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkRenameHtmlFilePathCurrentFileNotExistNewFileExist()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		Component component = study.getFirstComponent();
		// Study not set automatically, weird!
		component.setStudy(study);

		File htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
				component.getHtmlFilePath());
		assertThat(htmlFile.exists());
		File differentHtmlFile = ioUtils.getFileInStudyAssetsDir(
				study.getDirName(), study.getLastComponent().getHtmlFilePath());
		assertThat(differentHtmlFile.exists());

		// Remove current HTML file
		htmlFile.delete();
		assertThat(!htmlFile.exists());

		// Rename to existing file - Current file doesn't exist - new file name
		// must be set and file still existing
		componentService.renameHtmlFilePath(component,
				study.getLastComponent().getHtmlFilePath());
		assertThat(component.getHtmlFilePath())
				.isEqualTo(study.getLastComponent().getHtmlFilePath());
		assertThat(differentHtmlFile.exists());

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkCloneComponent()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);

		Component original = study.getFirstComponent();
		Component clone = componentService.clone(original);

		// Equal
		assertThat(clone.getComments()).isEqualTo(original.getComments());
		assertThat(clone.getHtmlFilePath())
				.isEqualTo(original.getHtmlFilePath());
		assertThat(clone.getJsonData()).isEqualTo(original.getJsonData());
		assertThat(clone.getTitle()).isEqualTo(original.getTitle());
		assertThat(clone.isActive()).isEqualTo(original.isActive());
		assertThat(clone.isReloadable()).isEqualTo(original.isReloadable());

		// Not equal
		assertThat(clone.getId()).isNotEqualTo(original.getId());
		assertThat(clone.getUuid()).isNotEqualTo(original.getUuid());

		// Check that cloned HTML file exists
		File clonedHtmlFile = ioUtils.getFileInStudyAssetsDir(
				study.getDirName(), clone.getHtmlFilePath());
		assertThat(clonedHtmlFile.isFile()).isTrue();

		// Clean-up
		removeStudy(study);
	}

	@Test
	public void checkCheckStandardForComponents()
			throws NoSuchAlgorithmException, IOException {
		Study study = importExampleStudy();
		addStudy(study);
		Component component = study.getFirstComponent();
		// Study not set automatically, weird!
		component.setStudy(study);

		try {
			checker.checkStandardForComponents(study.getId(),
					component.getId(), component);
		} catch (BadRequestException e) {
			Fail.fail();
		}

		long nonExistentStudyId = 2l;
		try {
			checker.checkStandardForComponents(nonExistentStudyId,
					component.getId(), component);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.componentNotBelongToStudy(
							nonExistentStudyId, component.getId()));
		}

		component.setStudy(null);
		try {
			checker.checkStandardForComponents(study.getId(),
					component.getId(), component);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.componentHasNoStudy(component.getId()));
		}

		component = null;
		try {
			checker.checkStandardForComponents(study.getId(), null,
					component);
			Fail.fail();
		} catch (BadRequestException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.componentNotExist(null));
		}

		// Clean-up
		removeStudy(study);
	}
}
