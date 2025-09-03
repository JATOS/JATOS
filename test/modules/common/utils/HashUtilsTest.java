package modules.common.utils;

import org.junit.Test;
import utils.common.HashUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the HashUtils class.
 */
public class HashUtilsTest {

    private static final String TEST_STRING = "test string for hashing";
    private static final String EXPECTED_MD5 = "d8e9a2389deec3ed61c03c7a83ced98f";
    private static final String EXPECTED_SHA256 = "e6569751329e15206c31e01f1bf2f7f249236d1394fc926ef39c6437f175cbe1";

    @Test
    public void testGetHashMD5() {
        String hash = HashUtils.getHashMD5(TEST_STRING);
        assertEquals(EXPECTED_MD5, hash);
    }

    @Test
    public void testGetHashWithAlgorithm() {
        String hash = HashUtils.getHash(TEST_STRING, HashUtils.SHA_256);
        assertEquals(EXPECTED_SHA256, hash);
    }

    @Test
    public void testGetHashWithFile() throws IOException {
        // Create a temporary file with test content
        Path tempFile = Files.createTempFile("hashtest", ".txt");
        Files.write(tempFile, TEST_STRING.getBytes(StandardCharsets.UTF_8));

        try {
            String hash = HashUtils.getHash(tempFile, HashUtils.SHA_256);
            assertEquals(EXPECTED_SHA256, hash);
        } finally {
            // Clean up
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testGenerateSecureRandomString() {
        // Test different lengths
        for (int length : new int[]{5, 10, 20}) {
            String random = HashUtils.generateSecureRandomString(length);
            assertEquals(length, random.length());
            // Verify it only contains allowed characters
            assertTrue(random.matches("[A-Za-z0-9]+"));
        }
    }

    @Test
    public void testGetChecksumString() {
        String checksum = HashUtils.getChecksum(TEST_STRING);
        assertEquals(6, checksum.length());
        assertEquals(EXPECTED_MD5.substring(0, 6), checksum);
    }

    @Test
    public void testGetChecksumFile() throws IOException {
        // Create a temporary file with test content
        Path tempFile = Files.createTempFile("checksumtest", ".txt");
        Files.write(tempFile, TEST_STRING.getBytes(StandardCharsets.UTF_8));
        File file = tempFile.toFile();

        try {
            long checksum = HashUtils.getChecksum(file);
            assertTrue(checksum > 0);

            // Test with empty file
            Files.write(tempFile, new byte[0]);
            long emptyChecksum = HashUtils.getChecksum(file);
            assertEquals(1, emptyChecksum); // Adler32 of empty file is 1
        } finally {
            // Clean up
            Files.deleteIfExists(tempFile);
        }
    }
}
