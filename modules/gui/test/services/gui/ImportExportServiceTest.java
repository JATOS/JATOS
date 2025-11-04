package services.gui;

import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import exceptions.gui.ForbiddenException;
import general.common.Common;
import models.common.Component;
import models.common.Study;
import models.common.User;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.mvc.Controller;
import testutils.gui.ContextMocker;
import utils.common.IOUtils;
import utils.common.JsonUtils;
import utils.common.ZipUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ImportExportService (uses Mockito to isolate dependencies).
 *
 * @author Kristian Lange
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "deprecation"})
public class ImportExportServiceTest {

    private static MockedStatic<Common> commonStatic;
    private static MockedStatic<ZipUtil> zipStatic;

    @BeforeClass
    public static void initStatics() {
        // Ensure temp and assets paths are inside a disposable temp dir
        String tmp = System.getProperty("java.io.tmpdir") + File.separator + "jatos-test";
        commonStatic = mockStatic(Common.class);
        commonStatic.when(Common::getTmpPath).thenReturn(tmp);
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(tmp);
        commonStatic.when(Common::getResultUploadsPath).thenReturn(tmp);
        commonStatic.when(Common::getStudyArchiveSuffix).thenReturn("jzip");

        zipStatic = mockStatic(ZipUtil.class);
        // For unzip, just create destination dir
        zipStatic.when(() -> ZipUtil.unzip(any(File.class), any(File.class))).thenAnswer(invocation -> {
            File dest = invocation.getArgument(1);
            dest.mkdirs();
            return null;
        });
        // For zipping, we don't need to really zip - just no-op
        zipStatic.when(() -> ZipUtil.zipFiles(anyList(), any(File.class))).thenAnswer(invocation -> null);
    }

    @AfterClass
    public static void tearDownStatics() {
        if (zipStatic != null) zipStatic.close();
        if (commonStatic != null) commonStatic.close();
    }

    private Checker checker;
    private StudyService studyService;
    private ComponentService componentService;
    private JsonUtils jsonUtils;
    private IOUtils ioUtils;
    private StudyDao studyDao;
    private ComponentDao componentDao;
    private StudyDeserializer studyDeserializer;

    private ImportExportService importExportService;

    private User user;

    @Before
    public void setup() {
        checker = mock(Checker.class);
        studyService = mock(StudyService.class);
        BatchService batchService = mock(BatchService.class);
        componentService = mock(ComponentService.class);
        jsonUtils = mock(JsonUtils.class);
        ioUtils = mock(IOUtils.class);
        studyDao = mock(StudyDao.class);
        componentDao = mock(ComponentDao.class);
        studyDeserializer = mock(StudyDeserializer.class);

        importExportService = new ImportExportService(checker, studyService, batchService, componentService,
                jsonUtils, ioUtils, studyDao, componentDao, studyDeserializer);

        user = new User();
        user.setUsername("tester");
        user.setName("Tester");
    }

    private Study exampleUploadedStudy() {
        Study s = new Study();
        s.setUuid("u-123");
        s.setTitle("My Study");
        s.setDirName("assetsA");
        // Add one component to also exercise checkForExistingComponents()
        Component c = new Component();
        c.setUuid("c-1");
        c.setTitle("Comp1");
        c.setStudy(s);
        s.getComponentList().add(c);
        return s;
    }

    private File prepareFakeUploadAndDeserializierer(Study uploadedStudy) throws Exception {
        // Create a temp zip file placeholder (content irrelevant due to mocked ZipUtil)
        File fakeZip = File.createTempFile("upload", ".zip");

        // When finding files for .jas inside the temp unzip dir, return a temp .jas file
        when(ioUtils.findFiles(any(File.class), anyString(), anyString())).thenAnswer(inv -> {
            File dir = inv.getArgument(0);
            File jas = new File(dir, "study.jas");
            jas.getParentFile().mkdirs();
            try (FileWriter fw = new FileWriter(jas)) {
                fw.write("{}");
            }
            return new File[]{jas};
        });

        when(studyDeserializer.deserialize(any(File.class))).thenReturn(uploadedStudy);

        // No existing components with same UUID in another study
        when(componentDao.findByUuid(anyString())).thenReturn(Optional.empty());

        return fakeZip;
    }

    private static class TempUnzipped {
        final File dir; // unzipped root
        final File assetsSubdir;
        final File jasFile;
        TempUnzipped(File dir, File assetsSubdir, File jasFile) {
            this.dir = dir; this.assetsSubdir = assetsSubdir; this.jasFile = jasFile;
        }
    }

    private TempUnzipped createUnzippedDirWithSingleAssetsDirAndJas() throws IOException {
        File root = new File(new File(Common.getTmpPath()), "JatosImportTest_" + System.nanoTime());
        if (!root.mkdirs()) throw new IOException("could not create temp import dir");
        File assets = new File(root, "assets");
        if (!assets.mkdirs()) throw new IOException("could not create assets dir");
        File jas = new File(root, "study.jas");
        try (FileWriter fw = new FileWriter(jas)) { fw.write("{}"); }
        return new TempUnzipped(root, assets, jas);
    }

    private Study makeStudy(String uuid, Long id, String title, String dirName) {
        Study s = new Study();
        s.setUuid(uuid);
        s.setId(id);
        s.setTitle(title);
        s.setDirName(dirName);
        return s;
    }

    @Test
    public void importStudy_newStudy_returnsExpectedJsonAndSetsSession() throws Exception {
        ContextMocker.mock();
        Study uploaded = exampleUploadedStudy();
        File fakeZip = prepareFakeUploadAndDeserializierer(uploaded);
        User user = new User("x", "X", "x@x");

        when(studyDao.findByUuid("u-123")).thenReturn(Optional.empty());
        when(ioUtils.checkStudyAssetsDirExists("assetsA")).thenReturn(false);

        ObjectNode json = importExportService.importStudy(user, fakeZip);

        assertThat(json.get("studyExists").asBoolean()).isFalse();
        assertThat(json.get("uuid").asText()).isEqualTo("u-123");
        assertThat(json.get("uploadedStudyTitle").asText()).isEqualTo("My Study");
        assertThat(json.get("uploadedDirName").asText()).isEqualTo("assetsA");
        assertThat(json.get("uploadedDirExists").asBoolean()).isFalse();

        // Session contains generated temp dir name
        String tempDirName = Controller.session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR);
        assertThat(tempDirName).isNotEmpty();

        // Cleanup should clear the session
        importExportService.cleanupAfterStudyImport();
        assertThat(Controller.session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR)).isNull();
    }

    @Test(expected = ForbiddenException.class)
    public void importStudy_whenStudyExistsAndUserNotMember_forbidden() throws Exception {
        ContextMocker.mock();
        Study uploaded = exampleUploadedStudy();
        File fakeZip = prepareFakeUploadAndDeserializierer(uploaded);
        User user = new User("y", "Y", "y@y");

        // The study to be uploaded exists already but does not has the user as a member
        Study existing = new Study();
        existing.setUuid("u-123");
        when(studyDao.findByUuid("u-123")).thenReturn(Optional.of(existing));
        when(ioUtils.checkStudyAssetsDirExists("assetsA")).thenReturn(true);

        // The existing study does not have the user as a member (has no members at all).
        // Therefore, ForbiddenException is expected.
        importExportService.importStudy(user, fakeZip);
    }

    @Test
    public void createStudyExportZipFile_shouldCallJsonAndZip_andReturnFile() throws Exception {
        // Given
        Study s = new Study();
        s.setTitle("Cool Study");
        s.setDirName("dir1");
        // Ensure study assets path resolves to an existing directory
        Path assets = Files.createTempDirectory("assetsDir1");
        when(ioUtils.generateFileName("Cool Study")).thenReturn("Cool_Study");
        when(ioUtils.generateStudyAssetsPath("dir1")).thenReturn(assets.toString());

        // We simulate that jsonUtils writes out a file successfully. The service creates a temp file and
        // calls jsonUtils.studyAsJsonForIO(study, thatFile). We don't need to do anything besides verify.

        // When
        File zip = importExportService.createStudyExportZipFile(s);

        // Then
        assertThat(zip).isNotNull();
        assertThat(zip.exists()).isTrue(); // created as a temp file by the method
        verify(jsonUtils).studyAsJsonForIO(eq(s), any(File.class));
        // ZipUtil.zipFiles called
        zipStatic.verify(() -> ZipUtil.zipFiles(anyList(), eq(zip)));

        // Cleanup
        zip.delete();
    }

    @Test
    public void importStudyConfirmed_overwriteExistingStudy_moveAssets_andUpdateWithoutDirName_whenKeepCurrentAssetsName() throws Exception {
        // Arrange
        TempUnzipped temp = createUnzippedDirWithSingleAssetsDirAndJas();
        // put temp dir name into session
        Controller.session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR, temp.dir.getName());

        Study uploaded = makeStudy("uuid-1", null, "Uploaded", "uploadedDir");
        Study current = makeStudy("uuid-1", 10L, "Current", "currentDir");
        // one matching component to trigger update path
        Component upComp = new Component(); upComp.setUuid("c-1"); uploaded.addComponent(upComp);
        Component curComp = new Component(); curComp.setUuid("c-1"); current.addComponent(curComp);

        when(ioUtils.findFiles(eq(temp.dir), eq(""), eq("jas"))).thenReturn(new File[]{ temp.jasFile });
        when(studyDeserializer.deserialize(temp.jasFile)).thenReturn(uploaded);
        when(studyDao.findByUuid("uuid-1")).thenReturn(Optional.of(current));
        when(ioUtils.findDirectories(temp.dir)).thenReturn(new File[]{ temp.assetsSubdir });

        // Act
        Long returnedId = importExportService.importStudyConfirmed(user, /*keepProperties*/false, /*keepAssets*/false,
                /*keepCurrentAssetsName*/true, /*renameAssets*/true);

        // Assert
        assertThat(returnedId).isEqualTo(10L);
        // permissions checked
        verify(checker).checkStandardForStudy(eq(current), eq(current.getId()), eq(user));
        verify(checker).checkStudyLocked(eq(current));
        // assets handling: remove old and move new with current dir name
        verify(ioUtils).removeStudyAssetsDir("currentDir");
        verify(ioUtils).moveStudyAssetsDir(eq(temp.assetsSubdir), eq("currentDir"));
        // properties updated without changing dir name
        verify(studyService).updateStudyWithoutDirName(eq(current), eq(uploaded), eq(user));
        // components updating called (we verify the interactions of componentService indirectly)
        verify(componentService, atLeastOnce()).updateProperties(any(Component.class), any(Component.class));
        verify(studyDao).update(eq(current));
        // cleanup not called here (separate method), ensure jas file delete attempted via actual code
        assertThat(temp.jasFile.exists()).isFalse();

        // no rename of assets dir should be tried in overwrite case
        verify(ioUtils, never()).findNonExistingStudyAssetsDirName(anyString());
    }

    @Test
    public void importStudyConfirmed_createNewStudy_whenNotExisting_andRenameAssetsIfNeeded() throws Exception {
        // Arrange
        TempUnzipped temp = createUnzippedDirWithSingleAssetsDirAndJas();
        Controller.session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR, temp.dir.getName());

        Study uploaded = makeStudy("uuid-2", null, "Uploaded2", "uploadedDir");
        when(ioUtils.findFiles(eq(temp.dir), eq(""), eq("jas"))).thenReturn(new File[]{ temp.jasFile });
        when(studyDeserializer.deserialize(temp.jasFile)).thenReturn(uploaded);
        when(studyDao.findByUuid("uuid-2")).thenReturn(Optional.empty());
        when(ioUtils.checkStudyAssetsDirExists("uploadedDir")).thenReturn(true);
        when(ioUtils.findNonExistingStudyAssetsDirName("uploadedDir")).thenReturn("uploadedDir_2");
        when(ioUtils.findDirectories(temp.dir)).thenReturn(new File[]{ temp.assetsSubdir });

        Study persisted = makeStudy("uuid-2", 42L, "Persisted", "uploadedDir_2");
        when(studyService.createAndPersistStudy(eq(user), any(Study.class))).thenReturn(persisted);

        // Act
        Long newId = importExportService.importStudyConfirmed(user, /*keepProperties*/false, /*keepAssets*/false,
                /*keepCurrentAssetsName*/false, /*renameAssets*/true);

        // Assert
        assertThat(newId).isEqualTo(42L);
        // assets moved to renamed dir
        verify(ioUtils).moveStudyAssetsDir(eq(temp.assetsSubdir), eq("uploadedDir_2"));
        // study persisted with possibly renamed dir name
        org.mockito.ArgumentCaptor<Study> studyCaptor = org.mockito.ArgumentCaptor.forClass(Study.class);
        verify(studyService).createAndPersistStudy(eq(user), studyCaptor.capture());
        assertThat(studyCaptor.getValue().getDirName()).isEqualTo("uploadedDir_2");
    }

    @Test(expected = RuntimeException.class)
    public void importStudyConfirmed_throwsIfNoTempDirInSession() throws Exception {
        // no session set
        importExportService.importStudyConfirmed(user, false, false, false, true);
    }

    @Test(expected = ForbiddenException.class)
    public void importStudyConfirmed_overwrite_existingStudy_forbidden() throws Exception {
        // Arrange
        TempUnzipped temp = createUnzippedDirWithSingleAssetsDirAndJas();
        Controller.session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR, temp.dir.getName());
        Study uploaded = makeStudy("uuid-3", null, "U", "udir");
        Study current = makeStudy("uuid-3", 77L, "C", "cdir");
        when(ioUtils.findFiles(eq(temp.dir), eq(""), eq("jas"))).thenReturn(new File[]{ temp.jasFile });
        when(studyDeserializer.deserialize(temp.jasFile)).thenReturn(uploaded);
        when(studyDao.findByUuid("uuid-3")).thenReturn(Optional.of(current));
        doThrow(new ForbiddenException("no")).when(checker).checkStandardForStudy(eq(current), eq(77L), eq(user));

        // Act
        importExportService.importStudyConfirmed(user, false, false, true, true);
    }
}
