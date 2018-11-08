package services.gui;

import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.ComponentDao;
import general.TestHelper;
import general.common.MessagesStrings;
import models.common.Component;
import models.common.Study;
import models.gui.ComponentProperties;
import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import utils.common.IOUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.NoSuchAlgorithmException;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests ComponentService
 *
 * @author Kristian Lange
 */
public class ComponentServiceTest {

    private Injector injector;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private ComponentService componentService;

    @Inject
    private ComponentDao componentDao;

    @Inject
    private IOUtils ioUtils;

    @Before
    public void startApp() throws Exception {
        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        injector = Guice.createInjector(builder.applicationModule());
        injector.injectMembers(this);
    }

    @After
    public void stopApp() throws Exception {
        // Clean up
        testHelper.removeAllStudies();
        testHelper.removeStudyAssetsRootDir();
        testHelper.removeAllStudyLogs();
    }

    @Test
    public void simpleCheck() {
        int a = 1 + 1;
        assertThat(a).isEqualTo(2);
    }

    /**
     * Test ComponentService.updateComponentAfterEdit
     */
    @Test
    public void checkUpdateComponentAfterEdit() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Component component = study.getFirstComponent().get();
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

        // Use ComponentService.updateComponentAfterEdit()
        Component updatedComponent = jpaApi.withTransaction(() -> {
            componentService.updateComponentAfterEdit(component, updatedProps);
            Component retrievedComponent =
                    componentDao.findByUuid(component.getUuid(), study).get();
            testHelper.fetchTheLazyOnes(retrievedComponent.getStudy());
            return retrievedComponent;
        });

        // Check unchanged fields
        assertThat(updatedComponent.isActive() == component.isActive()).isTrue();
        assertThat(updatedComponent.getId().equals(component.getId())).isTrue();
        assertThat(updatedComponent.getStudy().equals(study)).isTrue();
        assertThat(updatedComponent.getUuid().equals(component.getUuid())).isTrue();
        assertThat(updatedComponent.getHtmlFilePath()).isEqualTo(component.getHtmlFilePath());

        // Check changed fields
        assertThat(updatedComponent.getComments()).isEqualTo(updatedProps.getComments());
        assertThat(updatedComponent.getJsonData()).isEqualTo("{}");
        assertThat(updatedComponent.getTitle()).isEqualTo(updatedProps.getTitle());
        assertThat(updatedComponent.isReloadable() == updatedProps.isReloadable()).isTrue();
    }

    /**
     * Test ComponentService.renameHtmlFilePath()
     */
    @Test
    public void checkRenameHtmlFilePath() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Component component = study.getFirstComponent().get();

        File htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
                component.getHtmlFilePath());
        assertThat(htmlFile.exists());

        // Call ComponentService.renameHtmlFilePath
        jpaApi.withTransaction(() -> {
            try {
                componentService.renameHtmlFilePath(component, "foo.html");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // Check standard renaming
        htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "foo.html");
        assertThat(component.getHtmlFilePath()).isEqualTo("foo.html");
        assertThat(htmlFile.exists());
    }

    /**
     * Test ComponentService.renameHtmlFilePath()
     */
    @Test
    public void checkRenameHtmlFilePathNewFileExists() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Component component = study.getFirstComponent().get();
        // Study not set automatically, weird!
        component.setStudy(study);

        File htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
                component.getHtmlFilePath());
        assertThat(htmlFile.exists());

        // Try renaming to existing file
        jpaApi.withTransaction(() -> {
            try {
                componentService.renameHtmlFilePath(component,
                        study.getLastComponent().get().getHtmlFilePath());
                Fail.fail();
            } catch (IOException e) {
                assertThat(e.getMessage()).isEqualTo(MessagesStrings
                        .htmlFileNotRenamedBecauseExists(component.getHtmlFilePath(), study
                                .getLastComponent().get().getHtmlFilePath()));
            }
        });

        // Everything is unchanged
        assertThat(component.getHtmlFilePath()).isEqualTo(htmlFile.getName());
        assertThat(htmlFile.exists());
    }

    /**
     * Test ComponentService.renameHtmlFilePath()
     */
    @Test
    public void checkRenameHtmlFilePathWithSubFolder() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Component component = study.getFirstComponent().get();
        // Study not set automatically, weird!
        component.setStudy(study);

        File htmlFile =
                ioUtils.getFileInStudyAssetsDir(study.getDirName(), component.getHtmlFilePath());
        assertThat(htmlFile.exists());

        // Create subfolder
        File subfolder = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "subfolder");
        subfolder.mkdir();
        assertThat(subfolder.exists());

        // Check renaming into a subfolder
        jpaApi.withTransaction(() -> {
            try {
                componentService.renameHtmlFilePath(component, "subfolder/foo.html");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "subfolder/foo.html");
        assertThat(component.getHtmlFilePath()).isEqualTo("subfolder/foo.html");
        assertThat(htmlFile.exists());
        assertThat(htmlFile.getParentFile().getName()).isEqualTo("subfolder");

        // Check renaming back into study assets
        jpaApi.withTransaction(() -> {
            try {
                componentService.renameHtmlFilePath(component, "foo.html");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "foo.html");
        assertThat(component.getHtmlFilePath()).isEqualTo("foo.html");
        assertThat(htmlFile.exists());
    }

    /**
     * Test ComponentService.renameHtmlFilePath()
     */
    @Test
    public void checkRenameHtmlFilePathEmptyNewFile() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Component component = study.getFirstComponent().get();
        // Study not set automatically, weird!
        component.setStudy(study);

        File htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
                component.getHtmlFilePath());
        assertThat(htmlFile.exists());

        // If new filename is empty leave the file alone and put "" into the db
        jpaApi.withTransaction(() -> {
            try {
                componentService.renameHtmlFilePath(component, "");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        assertThat(component.getHtmlFilePath()).isEqualTo("");
        assertThat(htmlFile.exists());
    }

    /**
     * Test ComponentService.renameHtmlFilePath()
     */
    @Test
    public void checkRenameHtmlFilePathCurrentFileNotExistNewFileNotExist() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Component component = study.getFirstComponent().get();
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
        jpaApi.withTransaction(() -> {
            try {
                componentService.renameHtmlFilePath(component, "foo.html");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(), "foo.html");
        assertThat(component.getHtmlFilePath()).isEqualTo("foo.html");
        assertThat(!htmlFile.exists());
    }

    /**
     * Test ComponentService.renameHtmlFilePath()
     */
    @Test
    public void checkRenameHtmlFilePathCurrentFileNotExistNewFileExist() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Component component = study.getFirstComponent().get();
        // Study not set automatically, weird!
        component.setStudy(study);

        File htmlFile = ioUtils.getFileInStudyAssetsDir(study.getDirName(),
                component.getHtmlFilePath());
        assertThat(htmlFile.exists());
        File differentHtmlFile = ioUtils.getFileInStudyAssetsDir(
                study.getDirName(), study.getLastComponent().get().getHtmlFilePath());
        assertThat(differentHtmlFile.exists());

        // Remove current HTML file
        htmlFile.delete();
        assertThat(!htmlFile.exists());

        // Rename to existing file - Current file doesn't exist - new file name
        // must be set and file still existing
        jpaApi.withTransaction(() -> {
            try {
                componentService.renameHtmlFilePath(component,
                        study.getLastComponent().get().getHtmlFilePath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        assertThat(component.getHtmlFilePath())
                .isEqualTo(study.getLastComponent().get().getHtmlFilePath());
        assertThat(differentHtmlFile.exists());
    }

    /**
     * Test ComponentService.clone()
     */
    @Test
    public void checkCloneComponent() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Component original = study.getFirstComponent().get();
        Component clone = componentService.clone(original);

        // Equal
        assertThat(clone.getComments()).isEqualTo(original.getComments());
        assertThat(clone.getHtmlFilePath()).isEqualTo(original.getHtmlFilePath());
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
    }

}
