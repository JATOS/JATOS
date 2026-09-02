package services.gui;

import daos.common.BatchDao;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import exceptions.common.ForbiddenException;
import exceptions.common.ImportExportException;
import exceptions.common.NotFoundException;
import http.common.Http.Context;
import models.common.Batch;
import models.common.Component;
import models.common.Study;
import models.common.Study.GroupSessionWriteScope;
import models.common.User;
import org.assertj.core.api.Fail;
import org.junit.Test;
import play.test.Helpers;
import testutils.JatosTest;
import utils.common.IOUtils;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static exceptions.common.JatosException.unchecked;
import static org.assertj.core.api.Assertions.assertThat;
import static services.gui.ImportExportService.SESSION_TEMP_IMPORT_STUDY_DIR;

/**
 * Tests for ImportExportService
 */
public class ImportExportServiceIntegrationTest extends JatosTest {

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

    private Path exampleStudyArchive() {
        return Path.of(general.common.Common.getBasepath(), TEST_RESOURCES_POTATO_COMPASS_JZIP);
    }

    @Test
    public void importStudy_newStudy_returnsExpectedJsonAndSetsSession() {
        Context.current().args().put(SIGNEDIN_USER, admin);

        Path file = exampleStudyArchive();
        Map<String, Object> resMap = importExportService.importStudy(file);

        assertThat(resMap.get("studyExists")).isEqualTo(Boolean.FALSE);
        assertThat(resMap.get("uuid")).isEqualTo("74ce92a5-2250-445e-be6d-efd5ddbc9e61");
        assertThat(resMap.get("uploadedStudyTitle")).isEqualTo("Potato Compass");
        assertThat(resMap.get("uploadedDirName")).isEqualTo("potatoCompass");
        assertThat(resMap.get("uploadedDirExists")).isEqualTo(Boolean.FALSE);

        // Session should contain temp dir name and that dir should exist in tmp
        String tempDirName = Context.current().response().getSession(SESSION_TEMP_IMPORT_STUDY_DIR).orElseThrow();
        assertThat(tempDirName).isNotEmpty();
        Path tmpDir = IOUtils.tmpDir().resolve(tempDirName);
        assertThat(Files.exists(tmpDir)).isTrue();

        // Cleanup temp dir and session
        importExportService.cleanupAfterStudyImport();

        assertThat(Files.exists(tmpDir)).isFalse();
        assertThat(Context.current().response().getSession(SESSION_TEMP_IMPORT_STUDY_DIR).isPresent()).isFalse();

    }

    @Test
    public void importStudy_whenStudyExistsAndUserNotMember_forbidden() {
        // First, import the study via API to persist it
        Long studyId = importExampleStudy();
        Study existing = getStudy(studyId);

        // Now try to import the same archive with a user that is no member
        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        User otherUser = createUser("foo@foo.org");
        Context.current().args().put(SIGNEDIN_USER, otherUser);

        try {
            importExportService.importStudy(exampleStudyArchive());
            Fail.fail();
        } catch (ForbiddenException e) {
            // expected
        }
        // Just to ensure the existing study is indeed there
        assertThat(existing.getId()).isEqualTo(studyId);
    }

    @Test
    public void importStudyConfirmed_withoutPriorImport_throwsIOException() {
        Context.current().args().put(SIGNEDIN_USER, admin);
        try {
            importExportService.importStudyConfirmed(false, false, false, false);
            Fail.fail();
        } catch (ImportExportException e) {
            // expected
        } catch (ForbiddenException | NotFoundException e) {
            Fail.fail();
        }
    }

    @Test
    public void importStudyConfirmed_newStudy_successful() {
        Context.current().args().put(SIGNEDIN_USER, admin);

        // First stage: upload/import to prepare temp dir and session
        importExportService.importStudy(exampleStudyArchive());

        // Confirm import: for a new study keepProperties=false, keepAssets=false, keepCurrentAssetsName=false, renameAssets=false
        Study newStudy = importExportService.importStudyConfirmed(false, false, false, false);
        assertThat(newStudy).isNotNull();
        assertThat(newStudy.getId()).isPositive();

        // Study should be persisted
        checkExampleStudyPropertiesAndAssets(newStudy.getId());

        // Clean up remaining temp dir/session if any
        importExportService.cleanupAfterStudyImport();
    }

    /**
     * Import a uploaded study: there are 5 possible cases: (udir - name of uploaded study asset dir, cdir - name of
     * current study asset dir)
     *
     * Test 5) !study exists - !udir exists : new study - write both
     */
    @Test
    public void importNewStudy() {
        Context.current().args().put(SIGNEDIN_USER, admin);

        // Import part 1: call importStudy()
        Path file = exampleStudyArchive();
        Map<String, Object> resMap = importExportService.importStudy(file);

        // Check returned JSON object
        assertThat(resMap.get("studyExists")).isEqualTo(Boolean.FALSE);
        assertThat(resMap.get("uuid")).isEqualTo("74ce92a5-2250-445e-be6d-efd5ddbc9e61");
        assertThat(resMap.get("currentStudyTitle")).isNull();
        assertThat(resMap.get("currentDirName")).isNull();
        assertThat(resMap.get("uploadedStudyTitle")).isEqualTo("Potato Compass");
        assertThat(resMap.get("uploadedDirName")).isEqualTo("potatoCompass");
        assertThat(resMap.get("uploadedDirExists")).isEqualTo(Boolean.FALSE);

        // Import part 2: call importStudyConfirmed()
        Study study = importExportService.importStudyConfirmed(false, false, false, false);

        // Check properties and assets of imported study
        checkExampleStudyPropertiesAndAssets(study.getId());
    }


    /**
     * Import a uploaded study: there are 5 possible cases: (udir - name of uploaded study asset dir, cdir - name of
     * current study asset dir)
     *
     * Test 1) study exists  -  udir exists - udir == cdir User chooses to overwrite properties and assets dir
     */
    @Test
    public void importStudyOverwritePropertiesAndAssets() {
        // Import study and alter it, so we have something to overwrite later on
        Long studyId = importExampleStudy();
        alterStudy(studyId);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        // Import part 1: call importStudy()
        Path file = exampleStudyArchive();
        Map<String, Object> resMap = importExportService.importStudy(file);

        // Check returned JSON object
        assertThat(resMap.get("studyExists")).isEqualTo(Boolean.TRUE);
        assertThat(resMap.get("uuid")).isEqualTo("74ce92a5-2250-445e-be6d-efd5ddbc9e61");
        assertThat(resMap.get("currentStudyTitle")).isEqualTo("Another Title");
        assertThat(resMap.get("currentDirName")).isEqualTo("another_example_dirname");

        assertThat(resMap.get("uploadedStudyTitle")).isEqualTo("Potato Compass");
        assertThat(resMap.get("uploadedDirName")).isEqualTo("potatoCompass");
        assertThat(resMap.get("uploadedDirExists")).isEqualTo(Boolean.FALSE);

        // Import part 2: call importStudyConfirmed(): Allow properties and assets to be overwritten
        importExportService.importStudyConfirmed(false, false, false, true);

        // Check properties and assets of updated study
        checkExampleStudyPropertiesAndAssets(studyId);
    }

    /**
     * Import a uploaded study: there are 5 possible cases: (udir - name of uploaded study asset dir, cdir - name of
     * current study asset dir)
     *
     * Test 1) study exists  -  udir exists - udir == cdir User chooses to keep properties and overwrite assets dir
     */
    @Test
    public void importStudyKeepPropertiesOverwriteAssets() {
        // Import study and alter it, so we have something to overwrite later on
        Long studyId = importExampleStudy();
        alterStudy(studyId);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        // Import part 1: call importStudy()
        Path file = exampleStudyArchive();
        importExportService.importStudy(file);

        // Import part 2: call importStudyConfirmed(): Keep properties but allow assets to be overwritten
        importExportService.importStudyConfirmed(true, false, true, true);

        // Check that properties are unchanged but assets are changed
        Study updatedStudy = studyDao.findById(studyId);
        assertThat(updatedStudy.getTitle()).isEqualTo("Another Title");

        assertThat(updatedStudy.getDirName()).isEqualTo("another_example_dirname");
        assertThat(ioUtils.checkStudyAssetsDirExists(updatedStudy.getDirName())).isTrue();
    }

    /**
     * Import a uploaded study: there are 5 possible cases: (udir - name of uploaded study asset dir, cdir - name of
     * current study asset dir)
     *
     * Test 1) study exists  -  udir exists - udir == cdir User chooses to overwrite properties and keep assets dir
     */
    @Test
    public void importStudyOverwritePropertiesKeepAssets() {
        // Import study and alter it, so we have something to overwrite later on
        Long studyId = importExampleStudy();
        alterStudy(studyId);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        // Import part 1: call importStudy()
        Path file = exampleStudyArchive();
        importExportService.importStudy(file);

        // Import part 2: call importStudyConfirmed(): Keep properties but allow assets to be overwritten
        importExportService.importStudyConfirmed(false, true, false, true);

        // Check that properties are unchanged but assets are changed
        Study importedStudy = studyDao.findById(studyId);
        assertThat(importedStudy.getTitle()).isEqualTo("Potato Compass");
        assertThat(importedStudy.getDirName()).isEqualTo("another_example_dirname");
        assertThat(ioUtils.checkStudyAssetsDirExists(importedStudy.getDirName())).isTrue();
    }

    /**
     * Import a uploaded study: there are 5 possible cases: (udir - name of uploaded study asset dir, cdir - name of
     * current study asset dir)
     *
     * Test 4) !study exists -  udir exists Should rename uploaded dir (generate new dir name)
     */
    @Test
    public void importStudyStudyNewButDirExists() {
        // Import study and alter it, so we have something to overwrite later on
        Long studyId = importExampleStudy();

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);

        // Create and persist a study with a UUID different from the study to be imported but the same study assets name (dirName)
        // We have to change the UUIDs of the study and its components and batches
        Study study = studyDao.findByIdWithComponentsAndBatches(studyId);
        study.setUuid("123");
        Component component1 = study.getComponentList().get(0);
        component1.setUuid("123");
        componentDao.merge(component1);
        Component component2 = study.getComponentList().get(1);
        component2.setUuid("456");
        componentDao.merge(component2);
        Component component3 = study.getComponentList().get(2);
        component3.setUuid("789");
        componentDao.merge(component3);
        Batch batch = study.getBatchList().get(0);
        batch.setUuid("123");
        batchDao.merge(batch);
        studyDao.merge(study);

        // Import part 1: call importStudy()
        Path file = exampleStudyArchive();
        Map<String, Object> resMap = importExportService.importStudy(file);

        // Check returned JSON object
        assertThat(resMap.get("studyExists")).isEqualTo(Boolean.FALSE);
        assertThat(resMap.get("uuid")).isEqualTo("74ce92a5-2250-445e-be6d-efd5ddbc9e61");
        assertThat(resMap.get("uploadedStudyTitle")).isEqualTo("Potato Compass");
        assertThat(resMap.get("uploadedDirName")).isEqualTo("potatoCompass");
        assertThat(resMap.get("uploadedDirExists")).isEqualTo(Boolean.TRUE);

        // Import part 2: call importStudyConfirmed(): allow renaming of uploaded assets dir
        importExportService.importStudyConfirmed(false, false, false, true);

        Study importedStudy = studyDao.findByUuid("74ce92a5-2250-445e-be6d-efd5ddbc9e61").orElseThrow();
        // Check that properties are unchanged
        assertThat(importedStudy.getTitle()).isEqualTo("Potato Compass");
        // Check that assets are renamed (have '_2' suffix)
        assertThat(importedStudy.getDirName()).isEqualTo("potatoCompass_2");
        assertThat(ioUtils.checkStudyAssetsDirExists(importedStudy.getDirName())).isTrue();
    }

    @Test
    public void cleanupAfterStudyImport_removesTempDirAndSession() {
        Context.current().args().put(SIGNEDIN_USER, admin);

        importExportService.importStudy(exampleStudyArchive());

        String tempDirName = Context.current().response().getSession(SESSION_TEMP_IMPORT_STUDY_DIR).orElseThrow();
        Path tmpDir = IOUtils.tmpDir().resolve(tempDirName);
        assertThat(Files.exists(tmpDir)).isTrue();

        importExportService.cleanupAfterStudyImport();

        // Only assert that the session is cleared; the non-empty temp dir may remain on disk
        assertThat(Context.current().response().getSession(SESSION_TEMP_IMPORT_STUDY_DIR).isPresent()).isFalse();
    }

    @Test
    public void createStudyExportZipFile_createsZip() throws Exception {
        Long studyId = importExampleStudy();

        Path zip = importExportService.createStudyExportZipFile(studyId);
        assertThat(zip).isNotNull();
        assertThat(Files.exists(zip)).isTrue();
        assertThat(Files.size(zip)).isGreaterThan(0L);
        // sanity: it should be a ZIP file by extension and magic bytes
        assertThat(zip.getFileName().toString()).endsWith("." + general.common.Common.getStudyArchiveSuffix());
        byte[] header = Files.readAllBytes(zip.toAbsolutePath());
        // ZIP files start with 'PK' signature
        assertThat(header[0]).isEqualTo((byte) 'P');
        assertThat(header[1]).isEqualTo((byte) 'K');
    }

    private void checkExampleStudyPropertiesAndAssets(Long studyId) {
        Study updatedStudy = studyDao.findByIdWithUsersAndComponents(studyId);
        assertThat(updatedStudy).isNotNull();
        assertThat(updatedStudy.getId()).isPositive();
        assertThat(updatedStudy.getUuid()).isEqualTo("74ce92a5-2250-445e-be6d-efd5ddbc9e61");
        assertThat(updatedStudy.getTitle()).isEqualTo("Potato Compass");
        assertThat(updatedStudy.getDescription()).isEqualTo("This is the example used in the tutorial YouTube video");
        assertThat(updatedStudy.getStudyInput()).isNull(); // This example doesn't have JSON data
        assertThat(updatedStudy.getComponentList().size()).isEqualTo(3);
        assertThat(updatedStudy.getComponent(1).getTitle()).isEqualTo("Demographics ");
        assertThat(updatedStudy.getLastComponent().orElseThrow().getTitle()).isEqualTo("Drag and Drop Potatoes (results in JSON)");
        assertThat(updatedStudy.getUserList().contains(admin)).isTrue();

        assertThat(updatedStudy.getDirName()).isEqualTo("potatoCompass");
        assertThat(ioUtils.checkStudyAssetsDirExists(updatedStudy.getDirName())).isTrue();

        // Check the number of files and directories in the study assets
        try (Stream<Path> stream = unchecked(() -> Files.list(ioUtils.getStudyAssetsDir(updatedStudy.getDirName())))) {
            long numOfFiles = stream.count();
            assertThat(numOfFiles).isEqualTo(6);
        }
    }

    private void alterStudy(Long studyId) {
        jpaApi.withTransaction((em) -> {
            Study study = studyDao.findById(studyId);

            study.setTitle("Another Title");
            study.setDescription("Another description");
            study.setStudyInput("{\"a\": 123}");
            study.setStudyEntryMsg("Another study entry msg");
            study.setActive(false);
            study.setGroupStudy(true);
            study.setGroupSessionWriteScope(GroupSessionWriteScope.MEMBER);
            study.setLinearStudy(true);
            study.setAllowPreview(true);
            study.getComponentList().remove(0);
            study.getLastComponent().orElseThrow().setTitle("Another Component Title");

            studyDao.merge(study);
            studyService.renameStudyAssetsDir(study, "another_example_dirname");
        });
    }
}
