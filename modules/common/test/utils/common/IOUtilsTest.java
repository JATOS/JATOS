package utils.common;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Tests for the IOUtils class.
 */
public class IOUtilsTest {

    private IOUtils ioUtils;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    private File testFile;
    private String testContent = "This is test content for IOUtils test.";

    @Before
    public void setUp() throws IOException {
        ioUtils = new IOUtils();
        testFile = tempFolder.newFile("test.txt");
        Files.write(testFile.toPath(), testContent.getBytes());
    }
    
    @After
    public void tearDown() {
        testFile = null;
    }

    @Test
    public void testReadFile() throws IOException {
        String content = ioUtils.readFile(testFile);
        assertEquals(testContent + System.lineSeparator(), content);
    }

    @Test
    public void testGenerateFileName() {
        // Test basic filename generation
        String filename = ioUtils.generateFileName("test file");
        assertEquals("test_file", filename);
        
        // Test with special characters
        filename = ioUtils.generateFileName("test?file*with/special\\chars");
        assertEquals("test_file_with_special_chars", filename);
        
        // Test with suffix
        filename = ioUtils.generateFileName("test file", "txt");
        assertEquals("test_file.txt", filename);
        
        // Test with very long name (should be truncated)
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longName.append("a");
        }
        filename = ioUtils.generateFileName(longName.toString());
        assertEquals(100, filename.length());
        
        // Test with very long name and suffix
        filename = ioUtils.generateFileName(longName.toString(), "txt");
        assertEquals(104, filename.length()); // 100 chars + ".txt"
        assertTrue(filename.endsWith(".txt"));
    }

    @Test
    public void testCheckFilename() {
        // Test valid filenames
        assertTrue(IOUtils.checkFilename("valid_filename"));
        assertTrue(IOUtils.checkFilename("valid-filename"));
        assertTrue(IOUtils.checkFilename("valid.filename"));
        assertTrue(IOUtils.checkFilename("valid123"));
        
        // Test invalid filenames
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
    public void testGenerateStudyAssetsPath() {
        String dirName = "testStudyAssets";
        String expectedPath = System.getProperty("jatos.studyAssetsRootPath", "study_assets_root") + 
                File.separator + dirName;
        
        String path = ioUtils.generateStudyAssetsPath(dirName);
        assertEquals(expectedPath, path);
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
    public void testGetResultUploadsDir() {
        Long studyResultId = 123L;
        Long componentResultId = 456L;
        
        // Test with just study result ID
        String path = IOUtils.getResultUploadsDir(studyResultId);
        String expectedPath = System.getProperty("jatos.resultUploadsPath", "result_uploads") + 
                File.separator + "study-result_123";
        assertEquals(expectedPath, path);
        
        // Test with both IDs
        path = IOUtils.getResultUploadsDir(studyResultId, componentResultId);
        expectedPath = System.getProperty("jatos.resultUploadsPath", "result_uploads") + 
                File.separator + "study-result_123" + File.separator + "comp-result_456";
        assertEquals(expectedPath, path);
    }
}