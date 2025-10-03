package utils.common;

import general.common.Common;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/**
 * Unit tests for ZipUtil.
 */
public class ZipUtilTest {

    private static MockedStatic<Common> commonMock;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @BeforeClass
    public static void mockCommon() {
        String tmp = System.getProperty("java.io.tmpdir");
        commonMock = Mockito.mockStatic(Common.class);
        commonMock.when(Common::getTmpPath).thenReturn(tmp);
        commonMock.when(Common::getStudyAssetsRootPath).thenReturn(tmp);
        commonMock.when(Common::getResultUploadsPath).thenReturn(tmp);
    }

    @AfterClass
    public static void closeMocks() {
        if (commonMock != null) commonMock.close();
    }

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testZipFilesAndUnzip_roundtrip() throws Exception {
        // Create two files to zip
        File f1 = temp.newFile("a.txt");
        File f2 = temp.newFile("b.txt");
        Files.write(f1.toPath(), "hello".getBytes());
        Files.write(f2.toPath(), "world".getBytes());

        File zip = temp.newFile("files.zip");
        ZipUtil.zipFiles(Arrays.asList(f1.toPath(), f2.toPath()), zip);

        // Verify zip contains both entries
        try (ZipFile zf = new ZipFile(zip)) {
            assertNotNull(zf.getEntry("a.txt"));
            assertNotNull(zf.getEntry("b.txt"));
        }

        // Unzip and verify content
        File dest = temp.newFolder("unzipped1");
        ZipUtil.unzip(zip, dest);
        assertEquals("hello", new String(Files.readAllBytes(dest.toPath().resolve("a.txt"))));
        assertEquals("world", new String(Files.readAllBytes(dest.toPath().resolve("b.txt"))));
    }

    @Test
    public void testAddDirToZip_preservesStructureAndSeparators() throws Exception {
        // Create directory structure
        File dir = temp.newFolder("dir");
        File sub = new File(dir, "sub");
        assertTrue(sub.mkdirs());
        File f1 = new File(dir, "root.txt");
        File f2 = new File(sub, "nested.txt");
        Files.write(f1.toPath(), "R".getBytes());
        Files.write(f2.toPath(), "N".getBytes());

        File zip = temp.newFile("structure.zip");
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zip.toPath())))) {
            // Purposefully pass a zipRoot containing a backslash to ensure normalization in entries
            Path zipRoot = Paths.get("root\\folder");
            ZipUtil.addToZip(out, zipRoot, dir.toPath());
            out.flush();
        }

        // Verify entries
        try (ZipFile zf = new ZipFile(zip)) {
            Set<String> names = new HashSet<>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) names.add(en.nextElement().getName());
            // Forward slashes only
            assertTrue(names.contains("root/folder/root.txt"));
            assertTrue(names.contains("root/folder/sub/nested.txt"));

            assertFalse(names.stream().anyMatch(n -> n.contains("\\")));

            // Also verify content
            assertEquals("R", readEntry(zf, "root/folder/root.txt"));
            assertEquals("N", readEntry(zf, "root/folder/sub/nested.txt"));
        }

        // Unzip and verify paths exist
        File dest = temp.newFolder("unzipped2");
        ZipUtil.unzip(zip, dest);
        assertTrue(new File(dest, "root/folder/root.txt").isFile());
        assertTrue(new File(dest, "root/folder/sub/nested.txt").isFile());
    }

    @Test
    public void testAddFileToZip_withZipRoot() throws Exception {
        File file = temp.newFile("data.txt");
        Files.write(file.toPath(), "DATA".getBytes());
        File zip = temp.newFile("rootfile.zip");

        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zip.toPath())))) {
            ZipUtil.addFileToZip(out, Paths.get("someRoot"), file.toPath());
        }

        try (ZipFile zf = new ZipFile(zip)) {
            ZipEntry entry = zf.getEntry("someRoot/data.txt");
            assertNotNull(entry);
            assertEquals("DATA", readEntry(zf, entry.getName()));
        }
    }

    @Test
    public void testAddFileToZip_withCustomNameAndRoot() throws Exception {
        File file = temp.newFile("x.bin");
        Files.write(file.toPath(), new byte[]{1,2,3});
        File zip = temp.newFile("customname.zip");

        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zip.toPath())))) {
            ZipUtil.addFileToZip(out, Paths.get("root"), Paths.get("custom/path.bin"), file.toPath());
        }

        try (ZipFile zf = new ZipFile(zip)) {
            ZipEntry entry = zf.getEntry("root/custom/path.bin");
            assertNotNull(entry);
            byte[] bytes = readAllBytes(zf, entry.getName());
            assertArrayEquals(new byte[]{1,2,3}, bytes);
        }
    }

    @Test
    public void testAddDataToZip() throws Exception {
        File zip = temp.newFile("data.zip");
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zip.toPath())))) {
            ZipUtil.addDataToZip(out, "Hello Data", "a/b/c.txt");
            // Ensure close of current entry so ZipFile can read it
            out.closeEntry();
        }

        try (ZipFile zf = new ZipFile(zip)) {
            assertEquals("Hello Data", readEntry(zf, "a/b/c.txt"));
        }
    }

    @Test
    public void testUnzip_preventsPathTraversal() throws Exception {
        // Create a malicious zip with an entry trying to escape the destination directory
        File zip = temp.newFile("malicious.zip");
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zip.toPath())))) {
            out.putNextEntry(new ZipEntry("../evil.txt"));
            out.write("evil".getBytes());
            out.closeEntry();
        }

        File dest = temp.newFolder("unzipped3");
        try {
            ZipUtil.unzip(zip, dest);
            fail("Expected IOException due to path traversal");
        } catch (IOException expected) {
            // ok
        }

        // Ensure file wasn't written
        assertFalse(new File(dest.getParentFile(), "evil.txt").exists());
    }

    private static String readEntry(ZipFile zf, String name) throws IOException {
        byte[] data = readAllBytes(zf, name);
        return new String(data);
    }

    private static byte[] readAllBytes(ZipFile zf, String name) throws IOException {
        try (InputStream is = zf.getInputStream(Objects.requireNonNull(zf.getEntry(name)));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            return baos.toByteArray();
        }
    }
}
