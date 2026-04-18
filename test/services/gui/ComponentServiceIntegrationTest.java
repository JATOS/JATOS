package services.gui;

import auth.gui.AuthService;
import com.pivovarit.function.ThrowingConsumer;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import general.common.RequestScope;
import models.common.Component;
import models.common.Study;
import models.gui.ComponentProperties;
import org.fest.assertions.Fail;
import org.junit.Test;
import testutils.ContextMocker;
import testutils.JatosTest;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.validation.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.pivovarit.function.ThrowingConsumer.unchecked;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for ComponentService
 *
 * @author Kristian Lange
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
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
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);

            Component original = study.getFirstComponent().get();

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
        }));
    }

    @Test
    public void bindToProperties_shouldReflectHtmlFileExists() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            Component component = study.getFirstComponent().get();

            ComponentProperties props = componentService.bindToProperties(component);

            assertThat(props.getUuid()).isEqualTo(component.getUuid());
            assertThat(props.getTitle()).isEqualTo(component.getTitle());
            assertThat(props.getId()).isEqualTo(component.getId());
            assertThat(props.getStudyId()).isEqualTo(study.getId());
            // html file should exist in imported example
            assertThat(ioUtils.checkFileInStudyAssetsDirExists(study.getDirName(), props.getHtmlFilePath())).isTrue();
            assertThat(props.isHtmlFileExists()).isTrue();
        }));
    }

    @Test
    public void updateComponentAfterEdit_shouldUpdateSelectedFields() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            Component component = study.getFirstComponent().get();
            String originalHtml = component.getHtmlFilePath();
            boolean originalActive = component.isActive();

            ComponentProperties updated = new ComponentProperties();
            updated.setTitle(component.getTitle() + " updated");
            updated.setComments("Some comment");
            updated.setComponentInput("{\"a\":1}");
            updated.setReloadable(!component.isReloadable());
            updated.setActive(!component.isActive());
            updated.setHtmlFilePath("shouldNotChange.html"); // will be ignored

            componentService.updateComponentAfterEdit(component, updated);

            Component reloaded = componentDao.findById(component.getId());
            assertThat(reloaded.getTitle()).isEqualTo(updated.getTitle());
            assertThat(reloaded.getComments()).isEqualTo(updated.getComments());
            assertThat(reloaded.getComponentInput()).isEqualTo(updated.getComponentInput());
            assertThat(reloaded.isReloadable()).isEqualTo(updated.isReloadable());
            assertThat(reloaded.isActive()).isEqualTo(updated.isActive());
            // unchanged
            assertThat(reloaded.getHtmlFilePath()).isEqualTo(originalHtml);
        }));
    }

    @Test
    public void createAndPersistComponent_fromProps_addsToStudyAndPersists() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
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

            Study reloaded = studyDao.findById(studyId);
            assertThat(reloaded.getComponentList().size()).isEqualTo(origSize + 1);
            assertThat(reloaded.getLastComponent().get().getTitle()).isEqualTo("New Component");
        }));
    }

    @Test
    public void renameHtmlFilePath_successfulRename() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            Component component = study.getFirstComponent().get();

            componentService.renameHtmlFilePath(component, "foo.html", true);

            Component reloaded = componentDao.findById(component.getId());
            assertThat(reloaded.getHtmlFilePath()).isEqualTo("foo.html");
            Path htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "foo.html");
            assertThat(Files.exists(htmlFile));
        }));
    }

    @Test
    public void renameHtmlFilePath_newHtmlFilePathExists() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            Component component = study.getFirstComponent().get();

            Path htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), component.getHtmlFilePath());
            assertThat(Files.exists(htmlFile));

            String existingHtmlFileName = study.getLastComponent().get().getHtmlFilePath();
            try {
                componentService.renameHtmlFilePath(component, existingHtmlFileName, true);
                Fail.fail();
            } catch (IOException e) {
                // expected
            }

            // Everything is unchanged
            Component reloaded = componentDao.findById(component.getId());
            assertThat(reloaded.getHtmlFilePath()).isEqualTo(htmlFile.getFileName().toString());
            assertThat(Files.exists(htmlFile));
        }));
    }

    @Test
    public void renameHtmlFilePath_withSubFolder() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            Component component = study.getFirstComponent().get();

            Path htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), component.getHtmlFilePath());
            assertThat(Files.exists(htmlFile));

            // Create subfolder
            Path subfolder = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "subfolder");
            Files.createDirectories(subfolder);
            assertThat(Files.exists(subfolder));

            // Changing the file path into a subfolder is possible
            componentService.renameHtmlFilePath(component, "subfolder/foo.html", true);

            // Check renaming into a subfolder
            htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "subfolder/foo.html");
            assertThat(component.getHtmlFilePath()).isEqualTo("subfolder/foo.html");
            assertThat(Files.exists(htmlFile));
            assertThat(htmlFile.getParent().getFileName().toString()).isEqualTo("subfolder");

            // Changing the file path back into the root of the study assets is also possible
            componentService.renameHtmlFilePath(component, "foo.html", true);

            // Check renaming back into the root of the study assets
            htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "foo.html");
            assertThat(component.getHtmlFilePath()).isEqualTo("foo.html");
            assertThat(Files.exists(htmlFile));
        }));
    }

    @Test
    public void renameHtmlFilePath_currentFileNotExistNewFileNotExist() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            Component component = study.getFirstComponent().get();

            Path htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), component.getHtmlFilePath());
            assertThat(Files.exists(htmlFile));

            // Remove current HTML file
            Files.delete(htmlFile);
            assertThat(!Files.exists(htmlFile));

            // Rename to non-existing file, AND current file doesn't exist
            // -> new file name must be set and file still doesn't existing
            componentService.renameHtmlFilePath(component, "foo.html", true);

            htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "foo.html");
            assertThat(component.getHtmlFilePath()).isEqualTo("foo.html");
            assertThat(Files.exists(htmlFile)).isFalse();
        }));
    }

    @Test
    public void renameHtmlFilePath_currentFileNotExistNewFileExist() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            Component component = study.getFirstComponent().get();

            Path htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), component.getHtmlFilePath());
            assertThat(Files.exists(htmlFile));

            Path differentHtmlFile = ioUtils.getFileInStudyAssetsDir(
                    study.getDirName(), study.getLastComponent().get().getHtmlFilePath());
            assertThat(Files.exists(differentHtmlFile));

            // Remove current HTML file
            Files.delete(htmlFile);
            assertThat(!Files.exists(htmlFile));

            // Rename to existing file AND current file doesn't exist
            // -> new file name must be set and file still existing
            componentService.renameHtmlFilePath(component, study.getLastComponent().get().getHtmlFilePath(), true);

            assertThat(component.getHtmlFilePath()).isEqualTo(study.getLastComponent().get().getHtmlFilePath());
            assertThat(Files.exists(differentHtmlFile));
        }));
    }


    @Test
    public void renameHtmlFilePath_withEmptyString() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            Component component = study.getFirstComponent().get();

            // If the html file name is empty an empty string should be set.
            componentService.renameHtmlFilePath(component, "", false);

            Component reloaded = componentDao.findById(component.getId());
            assertThat(reloaded.getHtmlFilePath()).isEqualTo("");
        }));
    }


    @Test
    public void validate_invalidTitle_shouldThrowValidationException() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            Component component = study.getFirstComponent().get();
            component.setTitle(""); // invalid
            try {
                componentService.validate(component);
                Fail.fail();
            } catch (ValidationException e) {
                // expected
            }
        }));
    }

    @Test
    public void getComponentFromIdOrUuid_shouldReturnValidComponent() {
        // Needs Play context for AuthService / RequestScope
        ContextMocker.mock();
        // put signed-in user into RequestScope for AuthService
        RequestScope.put(AuthService.SIGNEDIN_USER, admin);
        Long studyId = importExampleStudy();

        jpaApi.withTransaction(ThrowingConsumer.unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            Component comp = study.getFirstComponent().get();

            // by ID
            Component byId = componentService.getComponentFromIdOrUuid(String.valueOf(comp.getId()));
            assertThat(byId.getId()).isEqualTo(comp.getId());
            // by UUID
            Component byUuid = componentService.getComponentFromIdOrUuid(comp.getUuid());
            assertThat(byUuid.getUuid()).isEqualTo(comp.getUuid());
        }));
    }

    @Test
    public void remove_shouldRemoveFromStudy() {
        Long studyId = importExampleStudy();
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            int origSize = study.getComponentList().size();

            // Create a new component and persist it
            ComponentProperties props = new ComponentProperties();
            props.setTitle("TempComp");
            Component temp = componentService.createAndPersistComponent(study, props);
            Long cid = temp.getId();

            // Sanity
            assertThat(studyDao.findById(studyId).getComponentList().size()).isEqualTo(origSize + 1);

            // Remove
            componentService.remove(componentDao.findById(cid), admin);

            Study reloaded = studyDao.findById(studyId);
            assertThat(reloaded.getComponentList().size()).isEqualTo(origSize);
            assertThat(componentDao.findById(cid)).isNull();
        }));
    }
}
