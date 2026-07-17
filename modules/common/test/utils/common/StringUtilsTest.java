package utils.common;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Tests for the StringUtils class.
 */
public class StringUtilsTest {

    @Test
    public void testHumanReadableByteCount() {
        // Test with small values
        assertEquals("0 B", StringUtils.humanReadableByteCount(0));
        assertEquals("999 B", StringUtils.humanReadableByteCount(999));
        assertEquals("-999 B", StringUtils.humanReadableByteCount(-999));

        // Test with kilobytes
        assertEquals("1.0 kB", StringUtils.humanReadableByteCount(1000));
        assertEquals("1.5 kB", StringUtils.humanReadableByteCount(1500));
        assertEquals("-1.5 kB", StringUtils.humanReadableByteCount(-1500));

        // Test with megabytes
        assertEquals("1.0 MB", StringUtils.humanReadableByteCount(1000000));
        assertEquals("1.5 MB", StringUtils.humanReadableByteCount(1500000));

        // Test with gigabytes
        assertEquals("1.0 GB", StringUtils.humanReadableByteCount(1000000000));
        assertEquals("1.5 GB", StringUtils.humanReadableByteCount(1500000000));

        // Test with terabytes
        assertEquals("1.0 TB", StringUtils.humanReadableByteCount(1000000000000L));
        assertEquals("1.5 TB", StringUtils.humanReadableByteCount(1500000000000L));
    }

    @Test
    public void testHumanReadableDuration() {
        // Test with seconds
        assertEquals("30s", StringUtils.humanReadableDuration(Duration.ofSeconds(30)));

        // Test with minutes and seconds
        assertEquals("5m 30s", StringUtils.humanReadableDuration(Duration.ofSeconds(330)));

        // Test with hours, minutes, and seconds
        assertEquals("2h 5m 30s", StringUtils.humanReadableDuration(Duration.ofSeconds(7530)));

        // Test with days (represented as hours)
        assertEquals("24h", StringUtils.humanReadableDuration(Duration.ofDays(1)));

        // Test with zero duration
        assertEquals("0s", StringUtils.humanReadableDuration(Duration.ZERO));
    }

    @Test
    public void testParseLong() {
        // Test with valid long
        Optional<Long> result = StringUtils.parseLong("123");
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(123), result.get());

        // Test with negative long
        result = StringUtils.parseLong("-123");
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(-123), result.get());

        // Test with invalid long (letters)
        result = StringUtils.parseLong("abc");
        assertFalse(result.isPresent());

        // Test with invalid long (mixed)
        result = StringUtils.parseLong("123abc");
        assertFalse(result.isPresent());

        // Test with empty string
        result = StringUtils.parseLong("");
        assertFalse(result.isPresent());

        // Test with null
        result = StringUtils.parseLong(null);
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetStringSize() {
        // Test with ASCII string
        assertEquals(4, StringUtils.getStringSize("test"));

        // Test with UTF-8 characters (each non-ASCII char takes more than 1 byte)
        assertEquals(6, StringUtils.getStringSize("äöü"));

        // Test with empty string
        assertEquals(0, StringUtils.getStringSize(""));

        // Test with null
        assertEquals(0, StringUtils.getStringSize(null));

        // Test with mixed ASCII and UTF-8
        String mixed = "test äöü";
        int expectedSize = mixed.getBytes(StandardCharsets.UTF_8).length;
        assertEquals(expectedSize, StringUtils.getStringSize(mixed));
    }

    @Test
    public void testGetDateTimeYyyyMMddHHmmss() {
        String dateTime = StringUtils.getDateTimeYyyyMMddHHmmss();

        assertNotNull(dateTime);
        assertTrue(dateTime.matches("\\d{14}"));
    }

    @Test
    public void testAnonymizeUsername() {
        assertEquals("unknown", StringUtils.anonymizeUsername(null));
        assertEquals("unknown", StringUtils.anonymizeUsername(""));
        assertEquals("unknown", StringUtils.anonymizeUsername("   "));
        assertEquals("unknown", StringUtils.anonymizeUsername("unknown"));

        assertEquals("*", StringUtils.anonymizeUsername("a"));
        assertEquals("**", StringUtils.anonymizeUsername("ab"));
        assertEquals("a*c", StringUtils.anonymizeUsername("abc"));
        assertEquals("a***e", StringUtils.anonymizeUsername("abcde"));
        assertEquals("ab***gh", StringUtils.anonymizeUsername("abcdefgh"));

        assertEquals("j**n@ex***le.com", StringUtils.anonymizeUsername("john@example.com"));
        assertEquals("**@ex***le.com", StringUtils.anonymizeUsername("ab@example.com"));
        assertEquals("j**n@loc***ost", StringUtils.anonymizeUsername("john@localhost"));
        assertEquals("j**n@", StringUtils.anonymizeUsername("john@"));
    }

    @Test
    public void testAnonymizePart() {
        assertEquals("", StringUtils.anonymizePart(null));
        assertEquals("", StringUtils.anonymizePart(""));
        assertEquals("", StringUtils.anonymizePart("   "));

        assertEquals("*", StringUtils.anonymizePart("a"));
        assertEquals("**", StringUtils.anonymizePart("ab"));
        assertEquals("a*c", StringUtils.anonymizePart("abc"));
        assertEquals("a**d", StringUtils.anonymizePart("abcd"));
        assertEquals("a***e", StringUtils.anonymizePart("abcde"));

        assertEquals("ab***gh", StringUtils.anonymizePart("abcdefgh"));
        assertEquals("abc***hij", StringUtils.anonymizePart("abcdefghij"));
    }

    @Test
    public void testAnonymizeDomain() {
        assertEquals("", StringUtils.anonymizeDomain(null));
        assertEquals("", StringUtils.anonymizeDomain(""));
        assertEquals("", StringUtils.anonymizeDomain("   "));

        assertEquals("loc***ost", StringUtils.anonymizeDomain("localhost"));
        assertEquals("ex***le.com", StringUtils.anonymizeDomain("example.com"));
        assertEquals("sub***ain.example", StringUtils.anonymizeDomain("subdomain.example"));
        assertEquals("ex***le.", StringUtils.anonymizeDomain("example."));
        assertEquals(".**m", StringUtils.anonymizeDomain(".com"));
    }
}