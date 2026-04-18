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
import java.nio.file.Path;

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
        String tmp = Path.of(System.getProperty("java.io.tmpdir"), "jatos-test").toString();
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
    public void testDeleteRecursivelyDeletesNestedDirectoryTree() throws Exception {
        Path root = tmp.newFolder("deleteRoot").toPath();
        Path nestedDir = root.resolve("a/b/c");
        Files.createDirectories(nestedDir);

        Path file1 = root.resolve("root.txt");
        Path file2 = root.resolve("a/b/file.txt");
        Path file3 = nestedDir.resolve("deep.txt");

        Files.write(file1, "root".getBytes(StandardCharsets.UTF_8));
        Files.write(file2, "nested".getBytes(StandardCharsets.UTF_8));
        Files.write(file3, "deep".getBytes(StandardCharsets.UTF_8));

        assertTrue(Files.exists(file1));
        assertTrue(Files.exists(file2));
        assertTrue(Files.exists(file3));

        IOUtils.deleteRecursively(root);

        assertFalse(Files.exists(file1));
        assertFalse(Files.exists(file2));
        assertFalse(Files.exists(file3));
        assertFalse(Files.exists(root));
    }

    @Test
    public void testDeleteRecursivelyDeletesSingleFile() throws Exception {
        Path file = tmp.newFile("single.txt").toPath();
        Files.write(file, "content".getBytes(StandardCharsets.UTF_8));

        assertTrue(Files.exists(file));

        IOUtils.deleteRecursively(file);

        assertFalse(Files.exists(file));
    }

    @Test
    public void testDeleteRecursivelyMissingPathThrows() {
        Path missing = tmp.getRoot().toPath().resolve("does-not-exist");

        try {
            IOUtils.deleteRecursively(missing);
            fail("Expected NoSuchFileException or IOException");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void testCopyRecursivelyCopiesNestedTree() throws Exception {
        Path source = tmp.newFolder("copySource").toPath();
        Path target = tmp.newFolder("copyTarget").toPath();

        Path nestedDir = source.resolve("a/b");
        Files.createDirectories(nestedDir);
        Path file1 = source.resolve("root.txt");
        Path file2 = source.resolve("a/child.txt");
        Path file3 = nestedDir.resolve("deep.txt");

        Files.write(file1, "root-content".getBytes(StandardCharsets.UTF_8));
        Files.write(file2, "child-content".getBytes(StandardCharsets.UTF_8));
        Files.write(file3, "deep-content".getBytes(StandardCharsets.UTF_8));

        IOUtils.copyRecursively(source, target);

        Path copied1 = target.resolve("root.txt");
        Path copied2 = target.resolve("a/child.txt");
        Path copied3 = target.resolve("a/b/deep.txt");

        assertTrue(Files.exists(target));
        assertTrue(Files.isDirectory(target.resolve("a")));
        assertTrue(Files.isDirectory(target.resolve("a/b")));
        assertTrue(Files.exists(copied1));
        assertTrue(Files.exists(copied2));
        assertTrue(Files.exists(copied3));

        assertEquals("root-content", Files.readString(copied1));
        assertEquals("child-content", Files.readString(copied2));
        assertEquals("deep-content", Files.readString(copied3));
    }

    @Test
    public void testCopyRecursivelyWithFilterCopiesOnlyMatchingFiles() throws Exception {
        Path source = tmp.newFolder("copySourceFiltered").toPath();
        Path target = tmp.newFolder("copyTargetFiltered").toPath();

        Path nestedDir = source.resolve("x/y");
        Files.createDirectories(nestedDir);
        Path keep1 = source.resolve("keep.txt");
        Path drop1 = source.resolve("drop.log");
        Path keep2 = nestedDir.resolve("keep2.txt");
        Path drop2 = nestedDir.resolve("drop2.md");

        Files.write(keep1, "k1".getBytes(StandardCharsets.UTF_8));
        Files.write(drop1, "d1".getBytes(StandardCharsets.UTF_8));
        Files.write(keep2, "k2".getBytes(StandardCharsets.UTF_8));
        Files.write(drop2, "d2".getBytes(StandardCharsets.UTF_8));

        IOUtils.copyRecursively(source, target, path -> path.getFileName().toString().endsWith(".txt"));

        assertTrue(Files.exists(target.resolve("keep.txt")));
        assertTrue(Files.exists(target.resolve("x/y/keep2.txt")));

        assertFalse(Files.exists(target.resolve("drop.log")));
        assertFalse(Files.exists(target.resolve("x/y/drop2.md")));
    }

    @Test
    public void testCopyRecursivelyOverwritesExistingTargetFiles() throws Exception {
        Path source = tmp.newFolder("copySourceOverwrite").toPath();
        Path target = tmp.newFolder("copyTargetOverwrite").toPath();

        Files.createDirectories(source.resolve("dir"));
        Files.write(source.resolve("dir/file.txt"), "source".getBytes(StandardCharsets.UTF_8));

        Files.createDirectories(target.resolve("dir"));
        Files.write(target.resolve("dir/file.txt"), "old-target".getBytes(StandardCharsets.UTF_8));

        IOUtils.copyRecursively(source, target);

        assertEquals("source", Files.readString(target.resolve("dir/file.txt")));
    }

    @Test
    public void testGetExistingFileSecurelyReturnsExistingFile() throws Exception {
        Path base = tmp.newFolder("secureBase").toPath();
        Path sub = base.resolve("sub");
        Files.createDirectories(sub);

        Path file = sub.resolve("file.txt");
        Files.write(file, "secure".getBytes(StandardCharsets.UTF_8));

        Path result = ioUtils.getExistingFileSecurely(base.toAbsolutePath().toString(), "sub/file.txt");

        assertEquals(file.toAbsolutePath(), result.toAbsolutePath());
        assertTrue(Files.exists(result));
        assertFalse(Files.isDirectory(result));
    }

    @Test
    public void testGetExistingFileSecurelyRejectsMissingFile() throws Exception {
        Path base = tmp.newFolder("secureBaseMissing").toPath();
        Files.createDirectories(base.resolve("sub"));

        try {
            ioUtils.getExistingFileSecurely(base.toAbsolutePath().toString(), "sub/missing.txt");
            fail("Expected IOException for missing file");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void testGetExistingFileSecurelyRejectsDirectory() throws Exception {
        Path base = tmp.newFolder("secureBaseDir").toPath();
        Path dir = base.resolve("subdir");
        Files.createDirectories(dir);

        try {
            ioUtils.getExistingFileSecurely(base.toAbsolutePath().toString(), "subdir");
            fail("Expected IOException for directory path");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void testGetExistingFileSecurelyRejectsPathTraversalAttack() throws Exception {
        Path base = tmp.newFolder("secureBaseTraversal").toPath();
        Path outside = tmp.newFolder("outsideTraversal").toPath();

        Path secret = outside.resolve("secret.txt");
        Files.write(secret, "secret".getBytes(StandardCharsets.UTF_8));

        Files.createDirectories(base.resolve("sub"));

        try {
            ioUtils.getExistingFileSecurely(base.toAbsolutePath().toString(), "../outsideTraversal/secret.txt");
            fail("Expected IOException for path traversal attempt");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void testGetExistingFileSecurelyRejectsAbsoluteFilePath() throws Exception {
        Path base = tmp.newFolder("secureBaseAbsolute").toPath();
        Path file = tmp.newFile("absolute.txt").toPath();
        Files.write(file, "absolute".getBytes(StandardCharsets.UTF_8));

        try {
            ioUtils.getExistingFileSecurely(base.toAbsolutePath().toString(), file.toAbsolutePath().toString());
            fail("Expected IOException for absolute file path");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void testGetExistingFileSecurelyRejectsBasePathThatIsNotAbsolute() throws Exception {
        Path relativeBase = Path.of("relative-base-test");
        Path file = tmp.newFile("relativeBaseFile.txt").toPath();

        try {
            ioUtils.getExistingFileSecurely(relativeBase.toString(), file.getFileName().toString());
            fail("Expected IOException for non-absolute base path");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void testGetExistingFileSecurelyRejectsTraversalEvenIfTargetExists() throws Exception {
        Path base = tmp.newFolder("secureBaseTraversalExisting").toPath();
        Path outside = tmp.newFolder("outsideTraversalExisting").toPath();

        Path secret = outside.resolve("secret.txt");
        Files.write(secret, "secret".getBytes(StandardCharsets.UTF_8));

        // Create a path that would exist if traversal were allowed
        Files.createDirectories(base.resolve("sub"));

        try {
            ioUtils.getExistingFileSecurely(base.toAbsolutePath().toString(), "../" + outside.getFileName() + "/secret.txt");
            fail("Expected IOException for traversal attempt");
        } catch (IOException expected) {
            // ok
        }
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
        longName.append("a".repeat(200));
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
    public void testGetResultsPathForJson() {
        Long studyResultId = 123L;
        Long componentResultId = 456L;

        String path = IOUtils.getResultsPathForJson(studyResultId, componentResultId);
        String expected = Path.of("/", "study_result_123", "comp-result_456").toString();

        assertEquals(expected, path);
    }

    @Test
    public void testGetResultsPathForJsonForZip() {
        Long studyResultId = 123L;
        Long componentResultId = 456L;

        String path = IOUtils.getResultsPathForZip(studyResultId, componentResultId);
        String expected = "study_result_123/comp-result_456";

        assertEquals(expected, path);
    }

    @Test
    public void testExistsAndSecure() throws Exception {
        Path base = tmp.newFolder("base").toPath();
        Path sub = base.resolve("sub");
        Files.createDirectories(sub);
        Path f = sub.resolve("test.txt");
        Files.write(f, "abc".getBytes(StandardCharsets.UTF_8));

        assertTrue(ioUtils.existsAndSecure(base.toAbsolutePath().toString(), "sub/test.txt"));
        assertFalse(ioUtils.existsAndSecure(base.toAbsolutePath().toString(), "sub/missing.txt"));
        // Path traversal should be rejected
        assertFalse(ioUtils.existsAndSecure(base.toAbsolutePath().toString(), "../etc/passwd"));
        // Absolute paths should be rejected
        assertFalse(ioUtils.existsAndSecure(base.toAbsolutePath().toString(), f.toAbsolutePath().toString()));
    }

    @Test
    public void testStudyAssetsDirOperations() throws Exception {
        Path assetsRoot = tmp.newFolder("assetsRoot").toPath();
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.toAbsolutePath().toString());

        // createStudyAssetsDir + check + generateStudyAssetsPath
        String dirName = "studyA";
        ioUtils.createStudyAssetsDir(dirName);
        assertTrue(ioUtils.checkStudyAssetsDirExists(dirName));
        assertEquals(assetsRoot.resolve(dirName), IOUtils.generateStudyAssetsPath(dirName));

        // findNonExistingStudyAssetsDirName
        assertEquals("studyA_2", ioUtils.findNonExistingStudyAssetsDirName("studyA"));

        // getStudyAssetsDir
        Path dir = ioUtils.getStudyAssetsDir(dirName);
        assertTrue(Files.exists(dir));

        // findFiles / findDirectories
        Path subdir = dir.resolve("sub");
        Files.createDirectories(subdir);
        Files.write(dir.resolve("a_pre_1_suf.txt"), new byte[]{1});
        Files.write(dir.resolve("pre_mid_suf"), new byte[]{1});
        Files.write(dir.resolve("nopre.txt"), new byte[]{1});
        Path[] files = ioUtils.findFiles(dir, "pre", "suf");
        assertEquals(1, files.length);
        Path[] dirs = ioUtils.findDirectories(dir);
        assertEquals(1, dirs.length);

        // renameStudyAssetsDir: rename to new name
        ioUtils.renameStudyAssetsDir("studyA", "studyB");
        assertFalse(Files.exists(assetsRoot.resolve("studyA")));
        assertTrue(Files.exists(assetsRoot.resolve("studyB")));

        // renameStudyAssetsDir: renaming to same name is a no-op
        ioUtils.renameStudyAssetsDir("studyB", "studyB");
        assertTrue(Files.exists(assetsRoot.resolve("studyB")));

        // removeStudyAssetsDir
        ioUtils.removeStudyAssetsDir("studyB");
        assertFalse(Files.exists(assetsRoot.resolve("studyB")));
    }

    @Test
    public void testGetFileInStudyAssetsDir_andExisting() throws Exception {
        Path assetsRoot = tmp.newFolder("assetsRoot2").toPath();
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.toAbsolutePath().toString());

        ioUtils.createStudyAssetsDir("studyX");
        Path target = assetsRoot.resolve("studyX").resolve("nested/file.txt");
        Files.createDirectories(target.getParent());
        Files.write(target, "x".getBytes(StandardCharsets.UTF_8));

        Path byPath = ioUtils.getFileInStudyAssetsDir("studyX", "nested/file.txt");
        assertEquals(target.toAbsolutePath(), byPath.toAbsolutePath());

        Path existing = ioUtils.getExistingFileInStudyAssetsDir("studyX", "nested/file.txt");
        assertNotNull(existing);

        Path notExisting = ioUtils.getExistingFileInStudyAssetsDir("studyX", "nested/missing.txt");
        assertNotNull(notExisting);
        assertFalse(Files.exists(notExisting));
    }

    @Test
    public void testGetFileInStudyAssetsDirRejectsMissingFilePath() throws Exception {
        Path assetsRoot = tmp.newFolder("assetsRootGetFileMissing").toPath();
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.toAbsolutePath().toString());

        ioUtils.createStudyAssetsDir("studyMissing");

        try {
            ioUtils.getFileInStudyAssetsDir("studyMissing", null);
            fail("Expected IOException for null file path");
        } catch (IOException expected) {
            // ok
        }

        try {
            ioUtils.getFileInStudyAssetsDir("studyMissing", "");
            fail("Expected IOException for empty file path");
        } catch (IOException expected) {
            // ok
        }

        try {
            ioUtils.getFileInStudyAssetsDir("studyMissing", "   ");
            fail("Expected IOException for blank file path");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void testGetFileInStudyAssetsDirRejectsPathTraversalAttack() throws Exception {
        Path assetsRoot = tmp.newFolder("assetsRootGetFileTraversal").toPath();
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.toAbsolutePath().toString());

        ioUtils.createStudyAssetsDir("studyTraversal");
        Path outside = tmp.newFolder("outsideTraversalGetFile").toPath();
        Path secret = outside.resolve("secret.txt");
        Files.write(secret, "secret".getBytes(StandardCharsets.UTF_8));

        try {
            ioUtils.getFileInStudyAssetsDir("studyTraversal", "../outsideTraversalGetFile/secret.txt");
            fail("Expected IOException for path traversal attempt");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void testGetFileInStudyAssetsDirRejectsAbsoluteFilePath() throws Exception {
        Path assetsRoot = tmp.newFolder("assetsRootGetFileAbsolute").toPath();
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.toAbsolutePath().toString());

        ioUtils.createStudyAssetsDir("studyAbsolute");
        Path absolute = tmp.newFile("absoluteGetFile.txt").toPath();
        Files.write(absolute, "abs".getBytes(StandardCharsets.UTF_8));

        try {
            ioUtils.getFileInStudyAssetsDir("studyAbsolute", absolute.toAbsolutePath().toString());
            fail("Expected IOException for absolute file path");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void testGetFileInStudyAssetsDirAllowsRelativeSubdirectoryPath() throws Exception {
        Path assetsRoot = tmp.newFolder("assetsRootGetFileRelative").toPath();
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.toAbsolutePath().toString());

        ioUtils.createStudyAssetsDir("studyRelative");
        Path target = assetsRoot.resolve("studyRelative").resolve("subdir/file.txt");
        Files.createDirectories(target.getParent());
        Files.write(target, "relative".getBytes(StandardCharsets.UTF_8));

        Path result = ioUtils.getFileInStudyAssetsDir("studyRelative", "subdir/file.txt");

        assertEquals(target.toAbsolutePath(), result.toAbsolutePath());
        assertTrue(Files.exists(result));
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
    public void testCloneComponentHtmlFileClonesRootHtmlFile() throws Exception {
        Path assetsRoot = tmp.newFolder("assetsRootCloneRoot").toPath();
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.toAbsolutePath().toString());

        String studyDir = "studyDir";
        ioUtils.createStudyAssetsDir(studyDir);

        Path original = assetsRoot.resolve(studyDir).resolve("index.html");
        Files.write(original, "<html>original</html>".getBytes(StandardCharsets.UTF_8));

        String clonedLocalPath = ioUtils.cloneComponentHtmlFile(studyDir, "index.html");

        assertEquals("index_clone.html", clonedLocalPath);
        Path cloned = assetsRoot.resolve(studyDir).resolve(clonedLocalPath);

        assertTrue(Files.exists(original));
        assertTrue(Files.exists(cloned));
        assertEquals("<html>original</html>", Files.readString(cloned));
    }

    @Test
    public void testCloneComponentHtmlFileClonesNestedHtmlFile() throws Exception {
        Path assetsRoot = tmp.newFolder("assetsRootCloneNested").toPath();
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.toAbsolutePath().toString());

        String studyDir = "studyNested";
        ioUtils.createStudyAssetsDir(studyDir);

        Path original = assetsRoot.resolve(studyDir).resolve("comp/sub/index.html");
        Files.createDirectories(original.getParent());
        Files.write(original, "<html>nested</html>".getBytes(StandardCharsets.UTF_8));

        String clonedLocalPath = ioUtils.cloneComponentHtmlFile(studyDir, "comp/sub/index.html");

        assertEquals("comp/sub/index_clone.html", clonedLocalPath);
        Path cloned = assetsRoot.resolve(studyDir).resolve(clonedLocalPath);

        assertTrue(Files.exists(original));
        assertTrue(Files.exists(cloned));
        assertEquals("<html>nested</html>", Files.readString(cloned));
    }

    @Test
    public void testCloneComponentHtmlFileCreatesNumericSuffixWhenCloneExists() throws Exception {
        Path assetsRoot = tmp.newFolder("assetsRootCloneSuffix").toPath();
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.toAbsolutePath().toString());

        String studyDir = "studySuffix";
        ioUtils.createStudyAssetsDir(studyDir);

        Path original = assetsRoot.resolve(studyDir).resolve("index.html");
        Files.write(original, "<html>original</html>".getBytes(StandardCharsets.UTF_8));

        Path firstClone = assetsRoot.resolve(studyDir).resolve("index_clone.html");
        Files.write(firstClone, "<html>existing clone</html>".getBytes(StandardCharsets.UTF_8));

        String clonedLocalPath = ioUtils.cloneComponentHtmlFile(studyDir, "index.html");

        assertEquals("index_1.html", clonedLocalPath);
        Path cloned = assetsRoot.resolve(studyDir).resolve(clonedLocalPath);

        assertTrue(Files.exists(cloned));
        assertEquals("<html>original</html>", Files.readString(cloned));
    }

    @Test
    public void testCloneComponentHtmlFileThrowsIfSourceIsMissing() throws Exception {
        Path assetsRoot = tmp.newFolder("assetsRootCloneMissing").toPath();
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.toAbsolutePath().toString());

        ioUtils.createStudyAssetsDir("studyMissing");

        try {
            ioUtils.cloneComponentHtmlFile("studyMissing", "missing.html");
            fail("Expected IOException for missing source file");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void testCloneComponentHtmlFileThrowsIfSourceIsDirectory() throws Exception {
        Path assetsRoot = tmp.newFolder("assetsRootCloneDir").toPath();
        commonStatic.when(Common::getStudyAssetsRootPath).thenReturn(assetsRoot.toAbsolutePath().toString());

        String studyDir = "studyDir";
        ioUtils.createStudyAssetsDir(studyDir);
        Files.createDirectories(assetsRoot.resolve(studyDir).resolve("comp"));

        try {
            ioUtils.cloneComponentHtmlFile(studyDir, "comp");
            fail("Expected IOException because source is a directory");
        } catch (IOException expected) {
            // ok
        }
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

        // Successfully rename
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
        Path uploadsRoot = tmp.newFolder("uploadsRoot").toPath();
        commonStatic.when(Common::getResultUploadsPath).thenReturn(uploadsRoot.toAbsolutePath().toString());

        long srId = 11L;
        long crId = 22L;

        // getResultUploadsDir (static) with mocked Common
        Path dir1 = IOUtils.getResultUploadsDir(srId);
        Path dir2 = IOUtils.getResultUploadsDir(srId, crId);
        assertEquals(uploadsRoot.resolve("study-result_" + srId).toAbsolutePath(), dir1);
        assertEquals(uploadsRoot.resolve(Path.of("study-result_" + srId, "comp-result_" + crId)).toAbsolutePath(), dir2);

        // getResultUploadFileSecurely prepares base dir; returned file may be in nested path
        Path secure = ioUtils.getResultUploadFileSecurely(srId, crId, "a/b/file.txt");
        assertTrue(Files.exists(dir2));
        // Ensure nested path exists before writing
        Files.createDirectories(secure.getParent());
        assertTrue(Files.exists(secure.getParent()));
        // Write a file and test size computing
        Files.write(secure, new byte[9]);
        long size = ioUtils.getResultUploadDirSize(srId);
        assertEquals(9, size);

        // removeResultUploadsDir (both overloads)
        ioUtils.removeResultUploadsDir(srId, crId);
        assertFalse(Files.exists(dir2));
        ioUtils.removeResultUploadsDir(srId);
        assertFalse(Files.exists(dir1));
    }

    @Test
    public void testMoveAndDetectOverwriteMovesWithoutOverwrite() throws Exception {
        Path sourceDir = tmp.newFolder("moveSourceNoOverwrite").toPath();
        Path targetDir = tmp.newFolder("moveTargetNoOverwriteParent").toPath();

        Path source = sourceDir.resolve("source.txt");
        Path target = targetDir.resolve("target.txt");

        Files.write(source, "source-content".getBytes(StandardCharsets.UTF_8));

        boolean overwritten = IOUtils.moveAndDetectOverwrite(source, target);

        assertFalse(overwritten);
        assertFalse(Files.exists(source));
        assertTrue(Files.exists(target));
        assertEquals("source-content", Files.readString(target));
    }

    @Test
    public void testMoveAndDetectOverwriteOverwritesExistingTarget() throws Exception {
        Path sourceDir = tmp.newFolder("moveSourceOverwrite").toPath();
        Path targetDir = tmp.newFolder("moveTargetOverwriteParent").toPath();

        Path source = sourceDir.resolve("source.txt");
        Path target = targetDir.resolve("target.txt");

        Files.write(source, "new-content".getBytes(StandardCharsets.UTF_8));
        Files.write(target, "old-content".getBytes(StandardCharsets.UTF_8));

        boolean overwritten = IOUtils.moveAndDetectOverwrite(source, target);

        assertTrue(overwritten);
        assertFalse(Files.exists(source));
        assertTrue(Files.exists(target));
        assertEquals("new-content", Files.readString(target));
    }

    @Test
    public void testMoveAndDetectOverwriteWorksForDirectories() throws Exception {
        Path sourceParent = tmp.newFolder("moveSourceDir").toPath();
        Path targetParent = tmp.newFolder("moveTargetDirParent").toPath();

        Path source = sourceParent.resolve("dirToMove");
        Path target = targetParent.resolve("dirMoved");

        Files.createDirectories(source.resolve("nested"));
        Files.write(source.resolve("nested/file.txt"), "dir-content".getBytes(StandardCharsets.UTF_8));

        boolean overwritten = IOUtils.moveAndDetectOverwrite(source, target);

        assertFalse(overwritten);
        assertFalse(Files.exists(source));
        assertTrue(Files.exists(target.resolve("nested/file.txt")));
        assertEquals("dir-content", Files.readString(target.resolve("nested/file.txt")));
    }

    @Test
    public void testMoveAndDetectOverwriteOverwritesExistingDirectory() throws Exception {
        Path sourceParent = tmp.newFolder("moveSourceDirOverwrite").toPath();
        Path targetParent = tmp.newFolder("moveTargetDirOverwriteParent").toPath();

        Path source = sourceParent.resolve("dirToMove");
        Path target = targetParent.resolve("dirMoved");

        Files.createDirectories(source.resolve("nested"));
        Files.write(source.resolve("nested/file.txt"), "new-dir-content".getBytes(StandardCharsets.UTF_8));

        Files.createDirectories(target.resolve("nested"));
        Files.write(target.resolve("nested/file.txt"), "old-dir-content".getBytes(StandardCharsets.UTF_8));

        boolean overwritten = IOUtils.moveAndDetectOverwrite(source, target);

        assertTrue(overwritten);
        assertFalse(Files.exists(source));
        assertTrue(Files.exists(target.resolve("nested/file.txt")));
        assertEquals("new-dir-content", Files.readString(target.resolve("nested/file.txt")));
    }
}
