package utils.common;

import general.common.Common;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for IOUtils
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class IOUtilsTest {

    private final IOUtils ioUtils = new IOUtils();

    private static MockedStatic<Common> commonStatic;

    @BeforeClass
    public static void initStatics() {
        // Ensure temp and assets paths are inside a disposable temp dir
        String tmp = System.getProperty("java.io.tmpdir") + File.separator + "jatos-test";
        commonStatic = mockStatic(Common.class);
        commonStatic.when(Common::getTmpPath).thenReturn(tmp);
    }

    @AfterClass
    public static void tearDownStatics() {
        if (commonStatic != null) commonStatic.close();
    }

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testReadFile() throws IOException {
        String testContent = "This is test content for IOUtils unit test.";
        File testFile = tmp.newFile("test.txt");
        Files.write(testFile.toPath(), testContent.getBytes());

        String content = ioUtils.readFile(testFile);
        assertEquals(testContent + System.lineSeparator(), content);
    }

    @Test
    public void testGenerateFileName() {
        // Basic filename generation
        String filename = ioUtils.generateFileName("test file");
        assertEquals("test_file", filename);

        // Special characters
        filename = ioUtils.generateFileName("test?file*with/special\\chars");
        assertEquals("test_file_with_special_chars", filename);

        // With suffix
        filename = ioUtils.generateFileName("test file", "txt");
        assertEquals("test_file.txt", filename);

        // Very long name (should be truncated to 100 chars)
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longName.append('a');
        }
        filename = ioUtils.generateFileName(longName.toString());
        assertEquals(100, filename.length());

        // Very long name with suffix
        filename = ioUtils.generateFileName(longName.toString(), "txt");
        assertEquals(104, filename.length()); // 100 + ".txt"
        assertTrue(filename.endsWith(".txt"));
    }

    @Test
    public void testCheckFilename() {
        // Valid filenames
        assertTrue(IOUtils.checkFilename("valid_filename"));
        assertTrue(IOUtils.checkFilename("valid-filename"));
        assertTrue(IOUtils.checkFilename("valid.filename"));
        assertTrue(IOUtils.checkFilename("valid123"));

        // Invalid filenames
        assertFalse(IOUtils.checkFilename("invalid filename")); // contains space
        assertFalse(IOUtils.checkFilename("invalid/filename")); // contains slash
        assertFalse(IOUtils.checkFilename("invalid\\filename")); // contains backslash
        assertFalse(IOUtils.checkFilename("invalid:filename")); // contains colon
        assertFalse(IOUtils.checkFilename("invalid?filename")); // contains question mark
        assertFalse(IOUtils.checkFilename("invalid*filename")); // contains asterisk
        assertFalse(IOUtils.checkFilename("invalid\"filename")); // contains quote
        assertFalse(IOUtils.checkFilename("invalid<filename")); // contains less than
        assertFalse(IOUtils.checkFilename("invalid>filename")); // contains greater than
        assertFalse(IOUtils.checkFilename("invalid|filename")); // contains pipe
    }

    @Test
    public void testGetResultsPath() {
        Long studyResultId = 123L;
        Long componentResultId = 456L;

        String path = IOUtils.getResultsPath(studyResultId, componentResultId);
        String expected = File.separator + "study_result_123" + File.separator + "comp-result_456";

        assertEquals(expected, path);
    }

    @Test
    public void testGetResultsPathForZip() {
        Long studyResultId = 123L;
        Long componentResultId = 456L;

        String path = IOUtils.getResultsPathForZip(studyResultId, componentResultId);
        String expected = "study_result_123/comp-result_456";

        assertEquals(expected, path);
    }

    @Test
    public void testExistsAndSecure() throws Exception {
        File base = tmp.newFolder("base");
        File sub = new File(base, "sub");
        assertTrue(sub.mkdirs());
        File f = new File(sub, "test.txt");
        Files.write(f.toPath(), "abc".getBytes(StandardCharsets.UTF_8));

        assertTrue(ioUtils.existsAndSecure(base.getAbsolutePath(), "sub/test.txt"));
        assertFalse(ioUtils.existsAndSecure(base.getAbsolutePath(), "sub/missing.txt"));
        // Path traversal should be rejected
        assertFalse(ioUtils.existsAndSecure(base.getAbsolutePath(), "../etc/passwd"));
        // Absolute paths should be rejected
        assertFalse(ioUtils.existsAndSecure(base.getAbsolutePath(), f.getAbsolutePath()));
    }

    @Test
    public void testStudyAssetsDirOperations() throws Exception {
        File assetsRoot = tmp.newFolder("assetsRoot");
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.getAbsolutePath());

        // createStudyAssetsDir + check + generateStudyAssetsPath
        String dirName = "studyA";
        ioUtils.createStudyAssetsDir(dirName);
        assertTrue(ioUtils.checkStudyAssetsDirExists(dirName));
        assertEquals(new File(assetsRoot, dirName).getAbsolutePath(), ioUtils.generateStudyAssetsPath(dirName));

        // findNonExistingStudyAssetsDirName
        assertEquals("studyA_2", ioUtils.findNonExistingStudyAssetsDirName("studyA"));

        // getStudyAssetsDir
        File dir = ioUtils.getStudyAssetsDir(dirName);
        assertTrue(dir.exists());

        // findFiles / findDirectories
        File subdir = new File(dir, "sub");
        assertTrue(subdir.mkdirs());
        Files.write(new File(dir, "a_pre_1_suf.txt").toPath(), new byte[]{1});
        Files.write(new File(dir, "pre_mid_suf").toPath(), new byte[]{1});
        Files.write(new File(dir, "nopre.txt").toPath(), new byte[]{1});
        File[] files = ioUtils.findFiles(dir, "pre", "suf");
        assertEquals(1, files.length);
        File[] dirs = ioUtils.findDirectories(dir);
        // Note: current implementation returns all entries (files and directories)
        assertEquals(4, dirs.length);

        // renameStudyAssetsDir: rename to new name
        ioUtils.renameStudyAssetsDir("studyA", "studyB");
        assertFalse(new File(assetsRoot, "studyA").exists());
        assertTrue(new File(assetsRoot, "studyB").exists());

        // renameStudyAssetsDir: renaming to same name is a no-op
        ioUtils.renameStudyAssetsDir("studyB", "studyB");
        assertTrue(new File(assetsRoot, "studyB").exists());

        // removeStudyAssetsDir
        ioUtils.removeStudyAssetsDir("studyB");
        assertFalse(new File(assetsRoot, "studyB").exists());
    }

    @Test
    public void testGetFileInStudyAssetsDir_andExisting() throws Exception {
        File assetsRoot = tmp.newFolder("assetsRoot2");
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.getAbsolutePath());
        ioUtils.createStudyAssetsDir("studyX");
        File target = new File(new File(assetsRoot, "studyX"), "nested/file.txt");
        assertTrue(target.getParentFile().mkdirs());
        Files.write(target.toPath(), "x".getBytes(StandardCharsets.UTF_8));

        File byPath = ioUtils.getFileInStudyAssetsDir("studyX", "nested/file.txt");
        assertEquals(target.getAbsolutePath(), byPath.getAbsolutePath());

        File existing = ioUtils.getExistingFileInStudyAssetsDir("studyX", "nested/file.txt");
        assertNotNull(existing);

        File notExisting = ioUtils.getExistingFileInStudyAssetsDir("studyX", "nested/missing.txt");
        assertNotNull(notExisting);
        assertFalse(notExisting.exists());
    }

    @Test
    public void testGetStudyAssetsDirSize() throws Exception {
        File assetsRoot = tmp.newFolder("assetsRoot3");
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.getAbsolutePath());
        ioUtils.createStudyAssetsDir("studySize");
        File base = new File(assetsRoot, "studySize");
        File a = new File(base, "a.bin");
        File b = new File(base, "sub/b.bin");
        assertTrue(b.getParentFile().mkdirs());
        Files.write(a.toPath(), new byte[5]);
        Files.write(b.toPath(), new byte[7]);

        long size = ioUtils.getStudyAssetsDirSize("studySize");
        assertEquals(12, size);
    }

    @Test
    public void testRenameHtmlFile() throws Exception {
        File assetsRoot = tmp.newFolder("assetsRoot4");
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.getAbsolutePath());
        String dirName = "studyHtml";
        ioUtils.createStudyAssetsDir(dirName);
        File base = new File(assetsRoot, dirName);
        File src = new File(base, "comp/index.html");
        assertTrue(src.getParentFile().mkdirs());
        Files.write(src.toPath(), "<html>1</html>".getBytes(StandardCharsets.UTF_8));

        // Successful rename
        ioUtils.renameHtmlFile("comp/index.html", "comp/new.html", dirName);
        assertFalse(src.exists());
        assertTrue(new File(base, "comp/new.html").exists());

        // No-op if old doesn't exist
        ioUtils.renameHtmlFile("comp/missing.html", "comp/another.html", dirName);

        // Conflict: target exists
        File other = new File(base, "comp/conflict.html");
        Files.write(other.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        File toRename = new File(base, "comp/tmp.html");
        Files.write(toRename.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        try {
            ioUtils.renameHtmlFile("comp/tmp.html", "comp/conflict.html", dirName);
            fail("Expected IOException due to existing target file");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void testCreateDir() throws Exception {
        File dir = new File(tmp.getRoot(), "created/inner");
        IOUtils.createDir(dir);
        assertTrue(dir.isDirectory());
    }

    @Test
    public void testCloneStudyAssetsDirectory() throws Exception {
        File assetsRoot = tmp.newFolder("assetsRoot_clone");
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.getAbsolutePath());
        String srcName = "srcStudy";
        ioUtils.createStudyAssetsDir(srcName);
        File srcDir = new File(assetsRoot, srcName);
        // add content
        File sub = new File(srcDir, "sub");
        assertTrue(sub.mkdirs());
        Files.write(new File(srcDir, "a.txt").toPath(), new byte[]{1, 2});
        Files.write(new File(sub, "b.txt").toPath(), new byte[]{3});

        String cloneName = ioUtils.cloneStudyAssetsDirectory(srcName);
        // First attempt should use _clone suffix
        assertEquals(srcName + "_clone", cloneName);
        File cloneDir = new File(assetsRoot, cloneName);
        assertTrue(cloneDir.isDirectory());
        assertTrue(new File(cloneDir, "a.txt").exists());
        assertTrue(new File(cloneDir, "sub/b.txt").exists());

        // Second clone should fall back to numeric suffix (since _clone already exists)
        String cloneName2 = ioUtils.cloneStudyAssetsDirectory(srcName);
        assertTrue(cloneName2.matches(srcName + "_\\d+"));
    }

    @Test
    public void testResultUploadsHelpers() throws Exception {
        File uploadsRoot = tmp.newFolder("uploadsRoot");
        commonStatic.when(Common::getResultUploadsPath).thenReturn(uploadsRoot.getAbsolutePath());

        long srId = 11L;
        long crId = 22L;

        // getResultUploadsDir (static) with mocked Common
        String dir1 = IOUtils.getResultUploadsDir(srId);
        String dir2 = IOUtils.getResultUploadsDir(srId, crId);
        assertEquals(new File(uploadsRoot, "study-result_" + srId).getAbsolutePath(), dir1);
        assertEquals(new File(uploadsRoot, "study-result_" + srId + File.separator + "comp-result_" + crId).getAbsolutePath(), dir2);

        // getResultUploadFileSecurely prepares base dir; returned file may be in nested path
        File secure = ioUtils.getResultUploadFileSecurely(srId, crId, "a/b/file.txt");
        assertTrue(new File(dir2).exists());
        // Ensure nested path exists before writing
        assertTrue(secure.getParentFile().mkdirs() || secure.getParentFile().exists());
        // Write a file and test size computing
        Files.write(secure.toPath(), new byte[9]);
        long size = ioUtils.getResultUploadDirSize(srId);
        assertEquals(9, size);

        // removeResultUploadsDir (both overloads)
        ioUtils.removeResultUploadsDir(srId, crId);
        assertFalse(new File(dir2).exists());
        ioUtils.removeResultUploadsDir(srId);
        assertFalse(new File(dir1).exists());
    }
}
