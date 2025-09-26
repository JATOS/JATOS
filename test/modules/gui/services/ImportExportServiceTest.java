package modules.gui.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pivovarit.function.ThrowingConsumer;
import com.pivovarit.function.ThrowingFunction;
import daos.common.BatchDao;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.User;
import org.fest.assertions.Fail;
import org.junit.Test;
import play.mvc.Controller;
import services.gui.ImportExportService;
import services.gui.StudyService;
import testutils.ContextMocker;
import testutils.JatosTest;
import utils.common.IOUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import static com.pivovarit.function.ThrowingConsumer.unchecked;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for ImportExportService
 *
 * Kristian Lange
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class ImportExportServiceTest extends JatosTest {

    @Inject
    private ImportExportService importExportService;

    @Inject
    private StudyDao studyDao;

    @Inject
    private ComponentDao componentDao;

    @Inject
    private BatchDao batchDao;

    @Inject
    private IOUtils ioUtils;

    @Inject
    private StudyService studyService;

    private File exampleStudyArchive() {
        String path = general.common.Common.getBasepath() + TEST_RESOURCES_POTATO_COMPASS_JZIP;
        return new File(path);
    }

    @Test
    public void importStudy_newStudy_returnsExpectedJsonAndSetsSession() {
        // Ensure Play context exists for session handling
        ContextMocker.mock();
        File file = exampleStudyArchive();

        ObjectNode response = jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> importExportService.importStudy(admin, file)));

        assertThat(response.get("studyExists").asBoolean()).isFalse();
        assertThat(response.get("uuid").asText()).isEqualTo("74ce92a5-2250-445e-be6d-efd5ddbc9e61");
        assertThat(response.get("uploadedStudyTitle").asText()).isEqualTo("Potato Compass");
        assertThat(response.get("uploadedDirName").asText()).isEqualTo("potatoCompass");
        assertThat(response.get("uploadedDirExists").asBoolean()).isFalse();

        // Session should contain temp dir name and that dir should exist in tmp
        String tempDirName = Controller.session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR);
        assertThat(tempDirName).isNotEmpty();
        File tmpDir = new File(IOUtils.TMP_DIR, tempDirName);
        assertThat(tmpDir.exists()).isTrue();

        // Cleanup temp dir and session
        importExportService.cleanupAfterStudyImport();
        // Only assert that the session is cleared; the non-empty temp dir may remain on disk
        assertThat(Controller.session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR)).isNull();
    }

    @Test
    public void importStudy_whenStudyExistsAndUserNotMember_forbidden() {
        // First import the study via API to persist it
        Long studyId = importExampleStudy();
        Study existing = getStudy(studyId);

        // Now try to import the same archive with a user that is no member
        User otherUser = createUser("foo@foo.org");
        ContextMocker.mock();
        jpaApi.withTransaction(ThrowingConsumer.unchecked((em) -> {
            try {
                importExportService.importStudy(otherUser, exampleStudyArchive());
                Fail.fail();
            } catch (ForbiddenException e) {
                // expected
            }
        }));
        // Just to ensure the existing study is indeed there
        assertThat(existing.getId()).isEqualTo(studyId);
    }

    @Test
    public void importStudyConfirmed_withoutPriorImport_throwsIOException() {
        ContextMocker.mock();
        try {
            importExportService.importStudyConfirmed(admin, false, false, false, false);
            Fail.fail();
        } catch (IOException e) {
            // expected
        } catch (ForbiddenException | NotFoundException e) {
            Fail.fail();
        }
    }

    @Test
    public void importStudyConfirmed_newStudy_successful() {
        ContextMocker.mock();

        // First stage: upload/import to prepare temp dir and session
        jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> importExportService.importStudy(admin, exampleStudyArchive())));

        // Confirm import: for a new study keepProperties=false, keepAssets=false, keepCurrentAssetsName=false, renameAssets=false
        Long newStudyId = jpaApi.withTransaction(ThrowingFunction.unchecked((em) ->
                importExportService.importStudyConfirmed(admin, false, false, false, false)));
        assertThat(newStudyId).isPositive();

        // Study should be persisted
        checkExampleStudyPropertiesAndAssets(newStudyId);

        // Clean up remaining temp dir/session if any
        importExportService.cleanupAfterStudyImport();
    }

    /**
     * Import a uploaded study: there are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     *
     * Test 5) !study exists - !udir exists : new study - write both
     */
    @Test
    public void importNewStudy() {
        ContextMocker.mock();

        // Import part 1: call importStudy()
        File file = exampleStudyArchive();
        ObjectNode response = jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> importExportService.importStudy(admin, file)));

        // Check returned JSON object
        assertThat(response.get("studyExists").asBoolean()).isFalse();
        assertThat(response.get("uuid").asText()).isEqualTo("74ce92a5-2250-445e-be6d-efd5ddbc9e61");
        assertThat(response.get("currentStudyTitle")).isNull();
        assertThat(response.get("currentDirName")).isNull();
        assertThat(response.get("uploadedStudyTitle").asText()).isEqualTo("Potato Compass");
        assertThat(response.get("uploadedDirName").asText()).isEqualTo("potatoCompass");
        assertThat(response.get("uploadedDirExists").asBoolean()).isFalse();

        // Import part 2: call importStudyConfirmed()
        Long studyId = jpaApi.withTransaction(ThrowingFunction.unchecked((em) ->
                importExportService.importStudyConfirmed(admin, false, false, false, false)));

        // Check properties and assets of imported study
        checkExampleStudyPropertiesAndAssets(studyId);
    }


    /**
     * Import a uploaded study: there are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     *
     * Test 1) study exists  -  udir exists - udir == cdir
     * User chooses to overwrite properties and assets dir
     */
    @Test
    public void importStudyOverwritePropertiesAndAssets() {
        ContextMocker.mock();

        // Import study and alter it, so we have something to overwrite later on
        Long studyId = importExampleStudy();
        alterStudy(studyId);

        // Import part 1: call importStudy()
        File file = exampleStudyArchive();
        ObjectNode response = jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> importExportService.importStudy(admin, file)));

        // Check returned JSON object
        assertThat(response.get("studyExists").asBoolean()).isTrue();
        assertThat(response.get("uuid").asText()).isEqualTo("74ce92a5-2250-445e-be6d-efd5ddbc9e61");
        assertThat(response.get("currentStudyTitle").asText()).isEqualTo("Another Title");
        assertThat(response.get("currentDirName").asText()).isEqualTo("another_example_dirname");

        assertThat(response.get("uploadedStudyTitle").asText()).isEqualTo("Potato Compass");
        assertThat(response.get("uploadedDirName").asText()).isEqualTo("potatoCompass");
        assertThat(response.get("uploadedDirExists").asBoolean()).isFalse();

        // Import part 2: call importStudyConfirmed(): Allow properties and assets to be overwritten
        jpaApi.withTransaction(ThrowingFunction.unchecked((em) ->
                importExportService.importStudyConfirmed(admin, false, false, false, true)));

        // Check properties and assets of updated study
        checkExampleStudyPropertiesAndAssets(studyId);
    }

    /**
     * Import a uploaded study: there are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     *
     * Test 1) study exists  -  udir exists - udir == cdir
     * User chooses to keep properties and overwrite assets dir
     */
    @Test
    public void importStudyKeepPropertiesOverwriteAssets() {
        ContextMocker.mock();

        // Import study and alter it, so we have something to overwrite later on
        Long studyId = importExampleStudy();
        alterStudy(studyId);

        // Import part 1: call importStudy()
        File file = exampleStudyArchive();
        jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> importExportService.importStudy(admin, file)));

        // Import part 2: call importStudyConfirmed(): Keep properties but allow assets to be overwritten
        jpaApi.withTransaction(ThrowingFunction.unchecked((em) ->
                importExportService.importStudyConfirmed(admin, true, false, true, true)));

        // Check that properties are unchanged but assets are changed
        jpaApi.withTransaction((em) -> {
            Study updatedStudy = studyDao.findById(studyId);
            assertThat(updatedStudy.getTitle()).isEqualTo("Another Title");

            assertThat(updatedStudy.getDirName()).isEqualTo("another_example_dirname");
            assertThat(ioUtils.checkStudyAssetsDirExists(updatedStudy.getDirName())).isTrue();
        });
    }

    /**
     * Import a uploaded study: there are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     *
     * Test 1) study exists  -  udir exists - udir == cdir
     * User chooses to overwrite properties and keep assets dir
     */
    @Test
    public void importStudyOverwritePropertiesKeepAssets() {
        ContextMocker.mock();

        // Import study and alter it, so we have something to overwrite later on
        Long studyId = importExampleStudy();
        alterStudy(studyId);

        // Import part 1: call importStudy()
        File file = exampleStudyArchive();
        jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> importExportService.importStudy(admin, file)));

        // Import part 2: call importStudyConfirmed(): Keep properties but allow assets to be overwritten
        jpaApi.withTransaction(ThrowingFunction.unchecked((em) ->
                importExportService.importStudyConfirmed(admin, false, true, false, true)));

        // Check that properties are unchanged but assets are changed
        jpaApi.withTransaction((em) -> {
            Study importedStudy = studyDao.findById(studyId);
            assertThat(importedStudy.getTitle()).isEqualTo("Potato Compass");
            assertThat(importedStudy.getDirName()).isEqualTo("another_example_dirname");
            assertThat(ioUtils.checkStudyAssetsDirExists(importedStudy.getDirName())).isTrue();
        });
    }

    /**
     * Import a uploaded study: there are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     *
     * Test 4) !study exists -  udir exists
     * Should rename uploaded dir (generate new dir name)
     */
    @Test
    public void importStudyStudyNewButDirExists() {
        ContextMocker.mock();

        // Import study and alter it, so we have something to overwrite later on
        Long studyId = importExampleStudy();

        // Create and persist a study with a UUID different from the study to be imported but the same study assets name (dirName)
        // We have to change the UUIDs of the study and its components and batches
        jpaApi.withTransaction((em) -> {
            Study study = studyDao.findById(studyId);
            study.setUuid("123");
            Component component1 = study.getComponentList().get(0);
            component1.setUuid("123");
            componentDao.update(component1);
            Component component2 = study.getComponentList().get(1);
            component2.setUuid("456");
            componentDao.update(component2);
            Component component3 = study.getComponentList().get(2);
            component3.setUuid("789");
            componentDao.update(component3);
            Batch batch = study.getBatchList().get(0);
            batch.setUuid("123");
            batchDao.update(batch);
            studyDao.update(study);
        });

        // Import part 1: call importStudy()
        File file = exampleStudyArchive();
        JsonNode response = jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> importExportService.importStudy(admin, file)));

        // Check returned JSON object
        assertThat(response.get("studyExists").asBoolean()).isFalse();
        assertThat(response.get("uuid").asText()).isEqualTo("74ce92a5-2250-445e-be6d-efd5ddbc9e61");
        assertThat(response.get("uploadedStudyTitle").asText()).isEqualTo("Potato Compass");
        assertThat(response.get("uploadedDirName").asText()).isEqualTo("potatoCompass");
        assertThat(response.get("uploadedDirExists").asBoolean()).isTrue();

        // Import part 2: call importStudyConfirmed(): allow renaming of uploaded assets dir
        jpaApi.withTransaction(ThrowingFunction.unchecked((em) ->
                importExportService.importStudyConfirmed(admin, false, false, false, true)));

        Study importedStudy = jpaApi.withTransaction(ThrowingFunction.unchecked((em) ->
                studyDao.findByUuid("74ce92a5-2250-445e-be6d-efd5ddbc9e61").get()));
        // Check that properties are unchanged
        assertThat(importedStudy.getTitle()).isEqualTo("Potato Compass");
        // Check that assets are renamed (have '_2' suffix)
        assertThat(importedStudy.getDirName()).isEqualTo("potatoCompass_2");
        assertThat(ioUtils.checkStudyAssetsDirExists(importedStudy.getDirName())).isTrue();
    }

    @Test
    public void cleanupAfterStudyImport_removesTempDirAndSession() {
        ContextMocker.mock();
        jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> importExportService.importStudy(admin, exampleStudyArchive())));
        String tempDirName = Controller.session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR);
        File tmpDir = new File(IOUtils.TMP_DIR, tempDirName);
        assertThat(tmpDir.exists()).isTrue();

        importExportService.cleanupAfterStudyImport();

        // Only assert that the session is cleared; the non-empty temp dir may remain on disk
        assertThat(Controller.session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR)).isNull();
    }

    @Test
    public void createStudyExportZipFile_createsZip() throws Exception {
        Long studyId = importExampleStudy();

        File zip = jpaApi.withTransaction(ThrowingFunction.unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            return importExportService.createStudyExportZipFile(study);
        }));
        assertThat(zip).isNotNull();
        assertThat(zip.exists()).isTrue();
        assertThat(zip.length()).isGreaterThan(0L);
        // sanity: it should be a ZIP file by extension and magic bytes
        assertThat(zip.getName()).endsWith("." + general.common.Common.getStudyArchiveSuffix());
        byte[] header = Files.readAllBytes(Paths.get(zip.getAbsolutePath()));
        // ZIP files start with 'PK' signature
        assertThat(header[0]).isEqualTo((byte) 'P');
        assertThat(header[1]).isEqualTo((byte) 'K');
    }

    private void checkExampleStudyPropertiesAndAssets(Long studyId) {
        jpaApi.withTransaction(unchecked((em) -> {
            Study updatedStudy = studyDao.findById(studyId);
            assertThat(updatedStudy).isNotNull();
            assertThat(updatedStudy.getId()).isPositive();
            assertThat(updatedStudy.getUuid()).isEqualTo("74ce92a5-2250-445e-be6d-efd5ddbc9e61");
            assertThat(updatedStudy.getTitle()).isEqualTo("Potato Compass");
            assertThat(updatedStudy.getDescription()).isEqualTo("This is the example used in the tutorial YouTube video");
            assertThat(updatedStudy.getJsonData()).isNull(); // This example doesn't have JSON data
            assertThat(updatedStudy.getComponentList().size()).isEqualTo(3);
            assertThat(updatedStudy.getComponent(1).getTitle()).isEqualTo("Demographics ");
            assertThat(updatedStudy.getLastComponent().get().getTitle()).isEqualTo("Drag and Drop Potatoes (results in JSON)");
            assertThat(updatedStudy.getUserList().contains(admin)).isTrue();

            assertThat(updatedStudy.getDirName()).isEqualTo("potatoCompass");
            assertThat(ioUtils.checkStudyAssetsDirExists(updatedStudy.getDirName())).isTrue();

            // Check the number of files and directories in the study assets
            String[] fileList = ioUtils.getStudyAssetsDir(updatedStudy.getDirName()).list();
            assertThat(Objects.requireNonNull(fileList).length).isEqualTo(6);
        }));
    }

    private void alterStudy(Long studyId) {
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);

            study.setTitle("Another Title");
            study.setDescription("Another description");
            study.setJsonData("{\"a\": 123}");
            study.setStudyEntryMsg("Another study entry msg");
            study.setActive(false);
            study.setGroupStudy(true);
            study.setLinearStudy(true);
            study.setAllowPreview(true);
            study.getComponentList().remove(0);
            study.getLastComponent().get().setTitle("Another Component Title");

            studyDao.update(study);
            studyService.renameStudyAssetsDir(study, "another_example_dirname");
        }));
    }
}
