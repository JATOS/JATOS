package services.gui;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.gui.ForbiddenException;
import general.TestHelper;
import models.common.Component;
import models.common.Study;
import models.common.User;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import utils.common.IOUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests ImportExportService
 *
 * @author Kristian Lange
 */
public class ImportExportServiceTest {

    private Injector injector;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private StudyService studyService;

    @Inject
    private ComponentService componentService;

    @Inject
    private StudyDao studyDao;

    @Inject
    private ComponentDao componentDao;

    @Inject
    private UserDao userDao;

    @Inject
    private IOUtils ioUtils;

    @Inject
    private ImportExportService importExportService;

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
     * Import a component that already exists in a study. It should be
     * overwritten.
     */
    @Test
    public void importExistingComponent() throws Exception {
        testHelper.mockContext();
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // First component of the study is the one in the component file
        File componentFile = getExampleComponentFile();
        FilePart<File> filePart = new FilePart<>(Component.COMPONENT,
                componentFile.getName(), "multipart/form-data", componentFile);

        // Call importComponent()
        ObjectNode jsonNode = jpaApi.withTransaction(() -> {
            try {
                return importExportService.importComponent(study, filePart);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        assertThat(jsonNode.get("componentExists").asBoolean()).isTrue();
        assertThat(jsonNode.get("componentTitle").asText()).isEqualTo("Quit button");

        // Change properties of first component, so we have something to check
        // later on
        Component firstComponent = jpaApi.withTransaction(() -> {
            Component component = study.getFirstComponent().get();
            component = testHelper.fetchTheLazyOnes(component);
            component.setTitle("Another Component Title");
            component.setActive(false);
            component.setComments("Another comments");
            component.setHtmlFilePath("anotherHtmlFilePath");
            component.setJsonData("{}");
            component.setReloadable(false);
            componentDao.update(component);
            return component;
        });

        // Call importComponentConfirmed(): Since the imported component is
        // already part of the study (at first position), it will be overwritten
        jpaApi.withTransaction(() -> {
            try {
                importExportService.importComponentConfirmed(study, componentFile.getName());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // Check that everything in the first component was updated
        Component updatedComponent = jpaApi.withTransaction(() -> {
            Component c = componentDao.findById(firstComponent.getId());
            testHelper.fetchTheLazyOnes(c.getStudy());
            return c;
        });

        // Check that IDs are unchanged
        assertThat(updatedComponent.getId()).isEqualTo(firstComponent.getId());
        assertThat(updatedComponent.getUuid()).isEqualTo(firstComponent.getUuid());

        // Check changed component properties
        assertThat(updatedComponent.getTitle()).isEqualTo("Another Component Title");
        assertThat(updatedComponent.getComments()).isEqualTo("Another comments");
        assertThat(updatedComponent.getHtmlFilePath()).isEqualTo("anotherHtmlFilePath");
        assertThat(updatedComponent.getJsonData()).isEqualTo("{}");
        assertThat(updatedComponent.getStudy()).isEqualTo(study);
        assertThat(updatedComponent.isActive()).isFalse();
        assertThat(updatedComponent.isReloadable()).isFalse();

        // Clean-up
        if (componentFile.exists()) {
            componentFile.delete();
        }
    }

    @Test
    public void importNewComponent() throws Exception {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        File componentFile = getExampleComponentFile();
        FilePart<File> filePart = new FilePart<>(Component.COMPONENT,
                componentFile.getName(), "multipart/form-data", componentFile);

        // Remove the last component (so we can import it again later on)
        Study studyWithoutLast = jpaApi.withTransaction(() -> {
            Study s = studyDao.findById(study.getId());
            componentService.remove(s.getLastComponent().get());
            return s;
        });

        // Check that the last component is removed
        assertThat(studyWithoutLast.getLastComponent().get().getTitle())
                .isNotEqualTo("Quit button");

        // Import 1. part: Call importComponent()
        ObjectNode jsonNode = jpaApi.withTransaction(() -> {
            try {
                return importExportService.importComponent(studyWithoutLast, filePart);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // Check returned JSON object
        assertThat(jsonNode.get("componentExists").asBoolean()).isFalse();
        assertThat(jsonNode.get("componentTitle").asText()).isEqualTo("Quit button");

        // Import 2. part: Call importComponentConfirmed(): The new component
        // will be put on the end of study's component list
        Study studyWithImportedComponent = jpaApi.withTransaction(() -> {
            try {
                Study s = studyDao.findById(study.getId());
                importExportService.importComponentConfirmed(s, componentFile.getName());
                return s;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // Check all properties of the imported component
        Component importedComponent = studyWithImportedComponent.getLastComponent().get();
        assertThat(study.getLastComponent().get().getTitle()).isEqualTo("Quit button");
        assertThat(importedComponent.getId()).isNotNull();
        assertThat(importedComponent.getUuid()).isEqualTo("503941c3-a0d5-43dc-ae56-083ab08df4b2");
        assertThat(importedComponent.getComments()).isEqualTo("");
        assertThat(importedComponent.getHtmlFilePath()).isEqualTo("quit_button.html");
        assertThat(importedComponent.getJsonData())
                .contains("This component is about what you can do in the client side");
        assertThat(importedComponent.getStudy()).isEqualTo(study);
        assertThat(importedComponent.isActive()).isTrue();
        assertThat(importedComponent.isReloadable()).isFalse();

        // Clean-up
        if (componentFile.exists()) {
            componentFile.delete();
        }
    }

    /**
     * Import a uploaded study: there are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     * <p>
     * Test 5) !study exists - !udir exists : new study - write both
     */
    @Test
    public void importNewStudy() throws Exception {
        // Import 1. part: Call importStudy()
        File studyFile = getExampleStudyFile();
        FilePart<File> filePart =
                new FilePart<>(Study.STUDY, studyFile.getName(), "multipart/form-data", studyFile);
        ObjectNode jsonNode = importStudy(filePart.getFile());

        // Check returned JSON object
        assertThat(jsonNode.get("studyExists").asBoolean()).isFalse();
        assertThat(jsonNode.get("uploadedStudyTitle").asText()).isEqualTo("Basic Example Study");
        assertThat(jsonNode.get("uploadedStudyUuid").asText())
                .isEqualTo("5c85bd82-0258-45c6-934a-97ecc1ad6617");
        assertThat(jsonNode.get("uploadedDirName").asText()).isEqualTo("basic_example_study");
        assertThat(jsonNode.get("uploadedDirExists").asBoolean()).isFalse();

        // Import 2. part: Call importStudyConfirmed(): Since this study is new,
        // the overwrite parameters don't matter
        ObjectNode node = Json.mapper().createObjectNode();
        node.put("overwriteStudysProperties", true);
        node.put("overwriteStudysDir", true);
        node.put("renameDir", false);
        importStudyConfirmed(node);

        // Check properties and assets of imported study
        checkPropertiesAndAssets("basic_example_study");
    }

    /**
     * Import a uploaded study: there are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     * <p>
     * Test 1) study exists  -  udir exists - udir == cdir
     * User chooses to overwrite properties and assets dir
     */
    @Test
    public void importStudyOverwritePropertiesAndAssets() throws Exception {
        testHelper.mockContext();

        // Import study and alter it, so we have something to overwrite later on
        Study study = getAlteredStudy();

        jpaApi.withTransaction(() -> {
            try {
                studyService.renameStudyAssetsDir(study, "another_example_dirname");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // Import 1. call: importStudy()
        File studyFile = getExampleStudyFile();
        FilePart<File> filePart = new FilePart<>(Study.STUDY,
                studyFile.getName(), "multipart/form-data", studyFile);
        ObjectNode jsonNode = importStudy(filePart.getFile());

        // Check returned JSON object
        assertThat(jsonNode.get("studyExists").asBoolean()).isTrue();
        assertThat(jsonNode.get("currentStudyTitle").asText()).isEqualTo("Another Title");
        assertThat(jsonNode.get("currentStudyUuid").asText())
                .isEqualTo("5c85bd82-0258-45c6-934a-97ecc1ad6617");
        assertThat(jsonNode.get("currentDirName").asText()).isEqualTo("another_example_dirname");
        assertThat(jsonNode.get("uploadedStudyTitle").asText()).isEqualTo("Basic Example Study");
        assertThat(jsonNode.get("uploadedStudyUuid").asText())
                .isEqualTo("5c85bd82-0258-45c6-934a-97ecc1ad6617");
        assertThat(jsonNode.get("uploadedDirName").asText()).isEqualTo("basic_example_study");
        assertThat(jsonNode.get("uploadedDirExists").asBoolean()).isFalse();

        // Import 2. call: importStudyConfirmed(): Allow properties and assets to be overwritten
        ObjectNode node = Json.mapper().createObjectNode();
        node.put("overwriteStudysProperties", true);
        node.put("overwriteStudysDir", true);
        node.put("keepCurrentDirName", false);
        importStudyConfirmed(node);

        // Check properties and assets of imported study
        checkPropertiesAndAssets("basic_example_study");
    }

    /**
     * Import a uploaded study: there are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     * <p>
     * Test 1) study exists  -  udir exists - udir == cdir
     * User chooses to keep properties and overwrite assets dir
     */
    @Test
    public void importStudyKeepPropertiesOverwriteAssets() throws Exception {
        testHelper.mockContext();

        // Import study and alter it, so we have something to overwrite later on
        Study study = getAlteredStudy();

        jpaApi.withTransaction(() -> {
            try {
                studyService.renameStudyAssetsDir(study, "another_example_dirname");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // Import 1. call: importStudy()
        File studyFile = getExampleStudyFile();
        FilePart<File> filePart = new FilePart<>(Study.STUDY,
                studyFile.getName(), "multipart/form-data", studyFile);
        importStudy(filePart.getFile());

        // Import 2. call: importStudyConfirmed(): Allow properties and assets to be overwritten
        ObjectNode node = Json.mapper().createObjectNode();
        node.put("overwriteStudysProperties", false);
        node.put("overwriteStudysDir", true);
        node.put("keepCurrentDirName", true);
        importStudyConfirmed(node);

        // Check that properties are unchanged
        jpaApi.withTransaction(() -> {
            Study importedStudy = studyDao.findById(study.getId());
            assertThat(importedStudy.getTitle()).isEqualTo("Another Title");
        });
        // todo Check that assets are different
        // todo check content of dir
        // Since properties aren't overwritten the dir name stays unchanged
        checkAssetsOfBasicExampleStudy(study, "another_example_dirname");
    }

    /**
     * Import a uploaded study: there are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     * <p>
     * Test 1) study exists  -  udir exists - udir == cdir
     * User chooses to overwrite properties and keep assets dir
     */
    @Test
    public void importStudyOverwritePropertiesKeepAssets() throws Exception {
        testHelper.mockContext();

        // Import study and alter it, so we have something to overwrite later on
        Study study = getAlteredStudy();

        jpaApi.withTransaction(() -> {
            try {
                studyService.renameStudyAssetsDir(study, "another_example_dirname");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // Import 1. call: importStudy()
        File studyFile = getExampleStudyFile();
        FilePart<File> filePart = new FilePart<>(Study.STUDY,
                studyFile.getName(), "multipart/form-data", studyFile);
        importStudy(filePart.getFile());

        // Import 2. call: importStudyConfirmed(): Allow properties and assets to be overwritten
        ObjectNode node = Json.mapper().createObjectNode();
        node.put("overwriteStudysProperties", true);
        node.put("overwriteStudysDir", false);
        node.put("keepCurrentDirName", false);
        importStudyConfirmed(node);

        // Check that properties are overwritten
        jpaApi.withTransaction(() -> {
            Study importedStudy = studyDao.findById(study.getId());
            assertThat(importedStudy.getTitle()).isEqualTo("Basic Example Study");
        });
        // Check that assets are unchanged
        checkAssetsOfBasicExampleStudy(study, "another_example_dirname");
    }

    /**
     * Import a uploaded study: there are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     * <p>
     * Test 4) !study exists -  udir exists
     * Should rename uploaded dir (generate new dir name)
     */
    @Test
    public void importStudyStudyNewButDirExists() throws Exception {
        testHelper.mockContext();

        // Create and persist a study with an UUID different from the study to be imported
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        study.setUuid("123");
        jpaApi.withTransaction(() -> studyDao.update(study));

        // Import 1. call: importStudy()
        File studyFile = getExampleStudyFile();
        FilePart<File> filePart = new FilePart<>(Study.STUDY,
                studyFile.getName(), "multipart/form-data", studyFile);
        ObjectNode jsonNode = importStudy(filePart.getFile());

        // Check returned JSON object
        assertThat(jsonNode.get("studyExists").asBoolean()).isFalse();
        assertThat(jsonNode.get("uploadedStudyTitle").asText()).isEqualTo("Basic Example Study");
        assertThat(jsonNode.get("uploadedStudyUuid").asText())
                .isEqualTo("5c85bd82-0258-45c6-934a-97ecc1ad6617");
        assertThat(jsonNode.get("uploadedDirName").asText()).isEqualTo("basic_example_study");
        assertThat(jsonNode.get("uploadedDirExists").asBoolean()).isTrue();

        // Import 2. call: importStudyConfirmed(): Allow properties and assets to be overwritten
        ObjectNode node = Json.mapper().createObjectNode();
        node.put("overwriteStudysProperties", true);
        node.put("overwriteStudysDir", true);
        node.put("renameDir", true);
        importStudyConfirmed(node);

        Study importedStudy = jpaApi.withTransaction(
                () -> studyDao.findByUuid("5c85bd82-0258-45c6-934a-97ecc1ad6617").get());
        // Check that properties are unchanged
        assertThat(importedStudy.getTitle()).isEqualTo("Basic Example Study");
        // Check that assets are renamed (have '_2' suffix)
        checkAssetsOfBasicExampleStudy(importedStudy, "basic_example_study_2");
    }

    /**
     * Import a uploaded study: there are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     * <p>
     * Test 2) study exists  -  udir exists - udir != cdir
     * User chooses to overwrite properties and overwrite assets dir; dir keeps cdir name
     */
    @Test
    public void importStudyOverwritePropertiesOverwriteAssetsButUdirExists() throws Exception {
        testHelper.mockContext();

        // Import study and alter it, so we have something to overwrite later on
        Study study = getAlteredStudy();
        jpaApi.withTransaction(() -> {
            try {
                studyService.renameStudyAssetsDir(study, "another_example_dirname");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // Create a assets dir that does not belong to the study
        ioUtils.createStudyAssetsDir("basic_example_study");

        // Import 1. call: importStudy()
        File studyFile = getExampleStudyFile();
        FilePart<File> filePart = new FilePart<>(Study.STUDY,
                studyFile.getName(), "multipart/form-data", studyFile);
        ObjectNode jsonNode = importStudy(filePart.getFile());

        // Check returned JSON object
        assertThat(jsonNode.get("studyExists").asBoolean()).isTrue();
        assertThat(jsonNode.get("currentStudyTitle").asText()).isEqualTo("Another Title");
        assertThat(jsonNode.get("currentStudyUuid").asText())
                .isEqualTo("5c85bd82-0258-45c6-934a-97ecc1ad6617");
        assertThat(jsonNode.get("currentDirName").asText()).isEqualTo("another_example_dirname");
        assertThat(jsonNode.get("uploadedStudyTitle").asText()).isEqualTo("Basic Example Study");
        assertThat(jsonNode.get("uploadedStudyUuid").asText())
                .isEqualTo("5c85bd82-0258-45c6-934a-97ecc1ad6617");
        assertThat(jsonNode.get("uploadedDirName").asText()).isEqualTo("basic_example_study");
        assertThat(jsonNode.get("uploadedDirExists").asBoolean()).isTrue();

        // Import 2. call: importStudyConfirmed(): Allow properties and assets to be overwritten
        ObjectNode node = Json.mapper().createObjectNode();
        node.put("overwriteStudysProperties", true);
        node.put("overwriteStudysDir", true);
        node.put("keepCurrentDirName", true);
        importStudyConfirmed(node);

        // Check that properties are unchanged
        jpaApi.withTransaction(() -> {
            Study importedStudy = studyDao.findById(study.getId());
            assertThat(importedStudy.getTitle()).isEqualTo("Basic Example Study");
        });

        // Check that dir name is unchanged
        checkAssetsOfBasicExampleStudy(study, "another_example_dirname");
    }

    @Test
    public void checkCreateStudyExportZipFile() throws IOException {
        testHelper.mockContext();

        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        // Export study into a file
        File studyFile = importExportService.createStudyExportZipFile(study);

        // Import the exported study again
        ObjectNode jsonNode = importStudy(studyFile);

        // Check returned JSON object
        assertThat(jsonNode.get("studyExists").asBoolean()).isTrue();
        assertThat(jsonNode.get("currentStudyTitle").asText()).isEqualTo("Basic Example Study");
        assertThat(jsonNode.get("currentStudyUuid").asText())
                .isEqualTo("5c85bd82-0258-45c6-934a-97ecc1ad6617");
        assertThat(jsonNode.get("currentDirName").asText()).isEqualTo("basic_example_study");
        assertThat(jsonNode.get("uploadedStudyTitle").asText()).isEqualTo("Basic Example Study");
        assertThat(jsonNode.get("uploadedStudyUuid").asText())
                .isEqualTo("5c85bd82-0258-45c6-934a-97ecc1ad6617");
        assertThat(jsonNode.get("uploadedDirName").asText()).isEqualTo("basic_example_study");
        assertThat(jsonNode.get("uploadedDirExists").asBoolean()).isTrue();

        // importStudy() should remember the study file name in the Play session
        String studyFileName = Http.Context.current.get().session()
                .get(ImportExportService.SESSION_UNZIPPED_STUDY_DIR);
        assertThat(studyFileName).startsWith("JatosImport_");
    }

    /**
     * Actually calls ImportExportService.importStudy()
     */
    private ObjectNode importStudy(File studyFile) {
        ObjectNode jsonNode = jpaApi.withTransaction(() -> {
            try {
                User admin = testHelper.getAdmin();
                return importExportService.importStudy(admin, studyFile);
            } catch (IOException | ForbiddenException e) {
                throw new RuntimeException(e);
            }
        });
        return jsonNode;
    }

    /**
     * Actually calls ImportExportService.importStudyConfirmed()
     */
    private void importStudyConfirmed(ObjectNode node) {
        jpaApi.withTransaction(() -> {
            try {
                User u = userDao.findByEmail(UserService.ADMIN_EMAIL);
                importExportService.importStudyConfirmed(u, node);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Makes a copy of the file 'test/resources/quit_button.jac' and returns the
     * copy's file object (quit_button_copy.jac)
     */
    private File getExampleComponentFile() throws IOException {
        File componentFile = new File("test/resources/quit_button.jac");
        File componentFileCopy = new File(IOUtils.TMP_DIR, "quit_button_copy.jac");
        FileUtils.copyFile(componentFile, componentFileCopy);
        return componentFileCopy;
    }

    /**
     * Makes a copy of the file 'test/resources/basic_example_study.zip' with
     * the name 'basic_example_study_copy.zip' and returns the copy's file
     * object. Since the study properties are unchanged the study properties
     * still point to a dirName of 'basic_example_study'.
     */
    private File getExampleStudyFile() throws IOException {
        final String basicExampleStudyZip = "test/resources/basic_example_study.zip";
        File studyFile = new File(basicExampleStudyZip);
        File studyFileCopy = new File(IOUtils.TMP_DIR, "basic_example_study_copy.zip");
        FileUtils.copyFile(studyFile, studyFileCopy);
        return studyFileCopy;
    }

    private void checkPropertiesAndAssets(String dirName) {
        jpaApi.withTransaction(() -> {
            List<Study> studyList = studyDao.findAll();
            User admin = testHelper.getAdmin();
            assertThat(studyList).hasSize(1);
            Study importedStudy = studyList.get(0);
            try {
                checkPropertiesOfBasicExampleStudy(importedStudy, admin);
                checkAssetsOfBasicExampleStudy(importedStudy, dirName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void checkPropertiesOfBasicExampleStudy(Study study, User admin) {
        assertThat(study.getComponentList().size()).isEqualTo(7);
        assertThat(study.getComponent(1).getTitle()).isEqualTo("Show JSON input ");
        assertThat(study.getLastComponent().get().getTitle()).isEqualTo("Quit button");
        assertThat(study.getDate()).isNull();
        assertThat(study.getDescription()).isEqualTo("A couple of sample components.");
        assertThat(study.getId()).isPositive();
        assertThat(study.getJsonData().contains("\"totalStudySlides\":17")).isTrue();
        assertThat(study.getUserList().contains(admin)).isTrue();
        assertThat(study.getTitle()).isEqualTo("Basic Example Study");
        assertThat(study.getUuid()).isEqualTo("5c85bd82-0258-45c6-934a-97ecc1ad6617");
    }

    private void checkAssetsOfBasicExampleStudy(Study study, String dirName) throws IOException {
        assertThat(study.getDirName()).isEqualTo(dirName);
        assertThat(ioUtils.checkStudyAssetsDirExists(study.getDirName())).isTrue();

        // Check the number of files and directories in the study assets
        String[] fileList = ioUtils.getStudyAssetsDir(study.getDirName()).list();
        assertThat(fileList.length).isEqualTo(11);
    }

    private Study getAlteredStudy() throws IOException {
        Study study = testHelper.importExampleStudy(injector);
        alterStudyProperties(study);
        jpaApi.withTransaction(() -> {
            User u = userDao.findByEmail(UserService.ADMIN_EMAIL);
            studyService.createAndPersistStudy(u, study);
        });
        return study;
    }

    private void alterStudyProperties(Study study) {
        study.getComponentList().remove(0);
        study.getLastComponent().get().setTitle("Another Component Title");
        study.setDescription("Another description");
        study.setJsonData("{}");
        study.setTitle("Another Title");
    }

}
