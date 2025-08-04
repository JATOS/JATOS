package utils.common;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Tests for the Helpers class.
 */
public class HelpersTest {

    @Test
    public void testHumanReadableByteCount() {
        // Test with small values
        assertEquals("0 B", Helpers.humanReadableByteCount(0));
        assertEquals("999 B", Helpers.humanReadableByteCount(999));
        assertEquals("-999 B", Helpers.humanReadableByteCount(-999));
        
        // Test with kilobytes
        assertEquals("1.0 kB", Helpers.humanReadableByteCount(1000));
        assertEquals("1.5 kB", Helpers.humanReadableByteCount(1500));
        assertEquals("-1.5 kB", Helpers.humanReadableByteCount(-1500));
        
        // Test with megabytes
        assertEquals("1.0 MB", Helpers.humanReadableByteCount(1000000));
        assertEquals("1.5 MB", Helpers.humanReadableByteCount(1500000));
        
        // Test with gigabytes
        assertEquals("1.0 GB", Helpers.humanReadableByteCount(1000000000));
        assertEquals("1.5 GB", Helpers.humanReadableByteCount(1500000000));
        
        // Test with terabytes
        assertEquals("1.0 TB", Helpers.humanReadableByteCount(1000000000000L));
        assertEquals("1.5 TB", Helpers.humanReadableByteCount(1500000000000L));
    }

    @Test
    public void testUrlEncode() {
        // Test with normal string
        assertEquals("test", Helpers.urlEncode("test"));
        
        // Test with spaces
        assertEquals("test+string", Helpers.urlEncode("test string"));
        
        // Test with special characters
        assertEquals("test%3Fstring%26more", Helpers.urlEncode("test?string&more"));
        
        // Test with UTF-8 characters
        assertEquals("%C3%A4%C3%B6%C3%BC", Helpers.urlEncode("äöü"));
        
        // Test with empty string
        assertEquals("", Helpers.urlEncode(""));
    }

    @Test
    public void testUrlDecode() {
        // Test with normal string
        assertEquals("test", Helpers.urlDecode("test"));
        
        // Test with encoded spaces
        assertEquals("test string", Helpers.urlDecode("test+string"));
        
        // Test with encoded special characters
        assertEquals("test?string&more", Helpers.urlDecode("test%3Fstring%26more"));
        
        // Test with encoded UTF-8 characters
        assertEquals("äöü", Helpers.urlDecode("%C3%A4%C3%B6%C3%BC"));
        
        // Test with empty string
        assertEquals("", Helpers.urlDecode(""));
        
        // Test with null
        assertNull(Helpers.urlDecode(null));
    }

    @Test
    public void testHumanReadableDuration() {
        // Test with seconds
        assertEquals("30s", Helpers.humanReadableDuration(Duration.ofSeconds(30)));
        
        // Test with minutes and seconds
        assertEquals("5m 30s", Helpers.humanReadableDuration(Duration.ofSeconds(330)));
        
        // Test with hours, minutes, and seconds
        assertEquals("2h 5m 30s", Helpers.humanReadableDuration(Duration.ofSeconds(7530)));
        
        // Test with days (represented as hours)
        assertEquals("24h", Helpers.humanReadableDuration(Duration.ofDays(1)));
        
        // Test with zero duration
        assertEquals("0s", Helpers.humanReadableDuration(Duration.ZERO));
    }

    @Test
    public void testParseLong() {
        // Test with valid long
        Optional<Long> result = Helpers.parseLong("123");
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(123), result.get());
        
        // Test with negative long
        result = Helpers.parseLong("-123");
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(-123), result.get());
        
        // Test with invalid long (letters)
        result = Helpers.parseLong("abc");
        assertFalse(result.isPresent());
        
        // Test with invalid long (mixed)
        result = Helpers.parseLong("123abc");
        assertFalse(result.isPresent());
        
        // Test with empty string
        result = Helpers.parseLong("");
        assertFalse(result.isPresent());
        
        // Test with null
        result = Helpers.parseLong(null);
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetStringSize() {
        // Test with ASCII string
        assertEquals(4, Helpers.getStringSize("test"));
        
        // Test with UTF-8 characters (each non-ASCII char takes more than 1 byte)
        assertEquals(6, Helpers.getStringSize("äöü"));
        
        // Test with empty string
        assertEquals(0, Helpers.getStringSize(""));
        
        // Test with null
        assertEquals(0, Helpers.getStringSize(null));
        
        // Test with mixed ASCII and UTF-8
        String mixed = "test äöü";
        int expectedSize = mixed.getBytes(StandardCharsets.UTF_8).length;
        assertEquals(expectedSize, Helpers.getStringSize(mixed));
    }
}