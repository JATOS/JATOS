package services.gui;

import daos.common.ComponentDao;
import daos.common.StudyDao;
import exceptions.common.ForbiddenException;
import exceptions.common.JatosException;
import exceptions.common.NotFoundException;
import http.common.Http.Context;
import models.common.Component;
import models.common.Study;
import models.gui.ComponentProperties;
import org.assertj.core.api.Fail;
import org.junit.Test;
import play.test.Helpers;
import testutils.JatosTest;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.validation.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ComponentService
 */
public class ComponentServiceIntegrationTest extends JatosTest {

    @Inject
    private ComponentService componentService;

    @Inject
    private IOUtils ioUtils;

    @Inject
    private StudyDao studyDao;

    @Inject
    private ComponentDao componentDao;

    @Test
    public void clone_shouldCopyFieldsAndGenerateNewUuid_withoutPersist() {
        Long studyId = importExampleStudy();
        Study study = studyDao.findByIdWithComponents(studyId);

        Component original = study.getFirstComponent().orElseThrow();

        Component clone = componentService.clone(original);

        // Same properties
        assertThat(clone.getStudy()).isEqualTo(original.getStudy());
        assertThat(clone.getTitle()).isEqualTo(original.getTitle());
        assertThat(clone.getHtmlFilePath()).isEqualTo(original.getHtmlFilePath());
        assertThat(clone.isReloadable()).isEqualTo(original.isReloadable());
        assertThat(clone.isActive()).isEqualTo(original.isActive());
        assertThat(clone.getComponentInput()).isEqualTo(original.getComponentInput());
        assertThat(clone.getComments()).isEqualTo(original.getComments());

        // Differences
        assertThat(clone.getId()).isNull(); // not persisted
        assertThat(clone.getUuid()).isNotEqualTo(original.getUuid());
        assertThat(clone.getUuid()).isNotEmpty();
    }

    @Test
    public void bindToProperties_shouldReflectHtmlFileExists() {
        Long studyId = importExampleStudy();
        Study study = studyDao.findByIdWithComponents(studyId);
        Component component = study.getFirstComponent().orElseThrow();

        ComponentProperties props = componentService.bindToProperties(component);

        assertThat(props.getUuid()).isEqualTo(component.getUuid());
        assertThat(props.getTitle()).isEqualTo(component.getTitle());
        assertThat(props.getId()).isEqualTo(component.getId());
        assertThat(props.getStudyId()).isEqualTo(study.getId());
        // html file should exist in imported example
        assertThat(ioUtils.checkFileInStudyAssetsDirExists(study.getDirName(), props.getHtmlFilePath())).isTrue();
        assertThat(props.isHtmlFileExists()).isTrue();
    }

    @Test
    public void updateComponentAfterEdit_shouldUpdateSelectedFields() throws IOException {
        Long studyId = importExampleStudy();
        Study study = studyDao.findByIdWithComponents(studyId);
        Component component = study.getFirstComponent().orElseThrow();

        ComponentProperties updated = new ComponentProperties();
        updated.setTitle(component.getTitle() + " updated");
        updated.setComments("Some comment");
        updated.setComponentInput("{\"a\":1}");
        updated.setReloadable(!component.isReloadable());
        updated.setHtmlFilePath("shouldNotChange.html"); // will be ignored
        updated.setActive(!component.isActive()); // will be ignored

        componentService.updateComponentAfterEdit(component, updated);

        Component reloaded = componentDao.findById(component.getId());
        assertThat(reloaded.getTitle()).isEqualTo(updated.getTitle());
        assertThat(reloaded.getComments()).isEqualTo(updated.getComments());
        assertThat(reloaded.getComponentInput()).isEqualTo(updated.getComponentInput());
        assertThat(reloaded.isReloadable()).isEqualTo(updated.isReloadable());
        assertThat(reloaded.getHtmlFilePath()).isEqualTo(updated.getHtmlFilePath());
        assertThat(reloaded.isActive()).isEqualTo(updated.isActive());
    }

    @Test
    public void createAndPersistComponent_fromProps_addsToStudyAndPersists() {
        Long studyId = importExampleStudy();
        Study study = studyDao.findByIdWithComponents(studyId);
        int origSize = study.getComponentList().size();

        ComponentProperties props = new ComponentProperties();
        props.setTitle("New Component");
        props.setHtmlFilePath("newComp.html");
        props.setReloadable(true);
        props.setComments("Hello");
        props.setComponentInput("{\"x\":2}");

        Component created = componentService.createAndPersistComponent(study, props);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getStudy()).isEqualTo(study);

        Study reloaded = studyDao.findByIdWithComponents(studyId);
        assertThat(reloaded.getComponentList().size()).isEqualTo(origSize + 1);
        assertThat(reloaded.getLastComponent().orElseThrow().getTitle()).isEqualTo("New Component");
    }

    @Test
    public void renameHtmlFilePath_successfulRename() throws IOException {
        Long studyId = importExampleStudy();
        Study study = studyDao.findByIdWithComponents(studyId);
        Component component = study.getFirstComponent().orElseThrow();

        componentService.renameHtmlFilePath(component, "foo.html", true);

        Component reloaded = componentDao.findById(component.getId());
        assertThat(reloaded.getHtmlFilePath()).isEqualTo("foo.html");
        Path htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "foo.html");
        assertThat(Files.exists(htmlFile)).isTrue();
    }

    @Test
    public void renameHtmlFilePath_newHtmlFilePathExists() throws IOException {
        Long studyId = importExampleStudy();
        Study study = studyDao.findByIdWithComponents(studyId);
        Component component = study.getFirstComponent().orElseThrow();

        Path htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), component.getHtmlFilePath());
        assertThat(Files.exists(htmlFile)).isTrue();

        String existingHtmlFileName = study.getLastComponent().orElseThrow().getHtmlFilePath();
        try {
            componentService.renameHtmlFilePath(component, existingHtmlFileName, true);
            Fail.fail();
        } catch (JatosException e) {
            assertThat(e.getCause()).isInstanceOf(IOException.class);
        }

        // Everything is unchanged
        Component reloaded = componentDao.findById(component.getId());
        assertThat(reloaded.getHtmlFilePath()).isEqualTo(htmlFile.getFileName().toString());
        assertThat(Files.exists(htmlFile)).isTrue();
    }

    @Test
    public void renameHtmlFilePath_withSubFolder() throws Exception {
        Long studyId = importExampleStudy();
        Study study = studyDao.findByIdWithComponents(studyId);
        Component component = study.getFirstComponent().orElseThrow();

        Path htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), component.getHtmlFilePath());
        assertThat(Files.exists(htmlFile)).isTrue();

        // Create subfolder
        Path subfolder = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "subfolder");
        Files.createDirectories(subfolder);
        assertThat(Files.exists(subfolder)).isTrue();

        // Changing the file path into a subfolder is possible
        componentService.renameHtmlFilePath(component, "subfolder/foo.html", true);

        // Check renaming into a subfolder
        htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "subfolder/foo.html");
        assertThat(component.getHtmlFilePath()).isEqualTo("subfolder/foo.html");
        assertThat(Files.exists(htmlFile)).isTrue();
        assertThat(htmlFile.getParent().getFileName().toString()).isEqualTo("subfolder");

        // Changing the file path back into the root of the study assets is also possible
        componentService.renameHtmlFilePath(component, "foo.html", true);

        // Check renaming back into the root of the study assets
        htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "foo.html");
        assertThat(component.getHtmlFilePath()).isEqualTo("foo.html");
        assertThat(Files.exists(htmlFile)).isTrue();
    }

    @Test
    public void renameHtmlFilePath_currentFileNotExistNewFileNotExist() throws IOException {
        Long studyId = importExampleStudy();
        Study study = studyDao.findByIdWithComponents(studyId);
        Component component = study.getFirstComponent().orElseThrow();

        Path htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), component.getHtmlFilePath());
        assertThat(Files.exists(htmlFile)).isTrue();

        // Remove current HTML file
        Files.delete(htmlFile);
        assertThat(Files.exists(htmlFile)).isFalse();

        // Rename to non-existing file AND current file doesn't exist
        // -> new file name must be set and file still doesn't existing
        componentService.renameHtmlFilePath(component, "foo.html", true);

        htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "foo.html");
        assertThat(component.getHtmlFilePath()).isEqualTo("foo.html");
        assertThat(Files.exists(htmlFile)).isFalse();
    }

    @Test
    public void renameHtmlFilePath_currentFileNotExistNewFileExist() throws IOException {
        Long studyId = importExampleStudy();
        Study study = studyDao.findByIdWithComponents(studyId);
        Component component = study.getFirstComponent().orElseThrow();

        Path htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), component.getHtmlFilePath());
        assertThat(Files.exists(htmlFile)).isTrue();

        Path differentHtmlFile = ioUtils.getFileInStudyAssetsDir(
                study.getDirName(), study.getLastComponent().orElseThrow().getHtmlFilePath());
        assertThat(Files.exists(differentHtmlFile)).isTrue();

        // Remove current HTML file
        Files.delete(htmlFile);
        assertThat(Files.exists(htmlFile)).isFalse();

        // Rename to existing file AND current file doesn't exist
        // -> new file name must be set and file still existing
        componentService.renameHtmlFilePath(component, study.getLastComponent().orElseThrow().getHtmlFilePath(), true);

        assertThat(component.getHtmlFilePath()).isEqualTo(study.getLastComponent().orElseThrow().getHtmlFilePath());
        assertThat(Files.exists(differentHtmlFile)).isTrue();
    }


    @Test
    public void renameHtmlFilePath_withEmptyString() {
        Long studyId = importExampleStudy();
        Study study = studyDao.findByIdWithComponents(studyId);
        Component component = study.getFirstComponent().orElseThrow();

        // If the html file name is empty an empty string should be set.
        componentService.renameHtmlFilePath(component, "", false);

        Component reloaded = componentDao.findById(component.getId());
        assertThat(reloaded.getHtmlFilePath()).isEqualTo("");
    }


    @Test
    public void validate_invalidTitle_shouldThrowValidationException() {
        Long studyId = importExampleStudy();
        Study study = studyDao.findByIdWithComponents(studyId);
        Component component = study.getFirstComponent().orElseThrow();
        component.setTitle(""); // invalid
        try {
            componentService.validate(component);
            Fail.fail();
        } catch (ValidationException e) {
            // expected
        }
    }

    @Test
    public void getComponentFromIdOrUuid_shouldReturnValidComponent() {
        Context.current().args().put(SIGNEDIN_USER, admin);

        Long studyId = importExampleStudy();

        Study study = studyDao.findByIdWithComponents(studyId);
        Component comp = study.getFirstComponent().orElseThrow();

        try {
            // by ID
            Component byId = componentService.getComponentFromIdOrUuid(String.valueOf(comp.getId()));
            assertThat(byId.getId()).isEqualTo(comp.getId());
            // by UUID
            Component byUuid = componentService.getComponentFromIdOrUuid(comp.getUuid());
            assertThat(byUuid.getUuid()).isEqualTo(comp.getUuid());
        } catch (NotFoundException | ForbiddenException e) {
            Fail.fail();
        }
    }

    @Test
    public void remove_shouldRemoveFromStudy() {
        Long studyId = importExampleStudy();
        Study study = studyDao.findByIdWithComponents(studyId);
        int origSize = study.getComponentList().size();

        // Create a new component and persist it
        ComponentProperties props = new ComponentProperties();
        props.setTitle("TempComp");
        Component temp = componentService.createAndPersistComponent(study, props);
        Long cid = temp.getId();

        // Sanity
        assertThat(studyDao.findByIdWithComponents(studyId).getComponentList().size()).isEqualTo(origSize + 1);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        // Remove
        jpaApi.withTransaction(em -> {
            componentService.remove(componentDao.findById(cid));
        });

        Study reloaded = studyDao.findByIdWithComponents(studyId);
        assertThat(reloaded.getComponentList().size()).isEqualTo(origSize);
        assertThat(componentDao.findById(cid)).isNull();
    }
}
