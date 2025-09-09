package modules.common.models;

import models.common.User;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests for the User class, normalizeUsername method
 */
public class UserTest {

    @Test
    public void returnsNullForNullInput() {
        assertNull(User.normalizeUsername(null));
    }

    @Test
    public void trimsWhitespaceAndLowercases() {
        assertEquals("user", User.normalizeUsername("  User  "));
        assertEquals("user", User.normalizeUsername("\tUser\n"));
    }

    @Test
    public void removesAccents() {
        assertEquals("arvid angstrom", User.normalizeUsername("Ärvíd Ångström"));
        assertEquals("cafe", User.normalizeUsername("Café"));
        // combining acute accent: e + ◌́
        assertEquals("cafe", User.normalizeUsername("Cafe\u0301"));
    }

    @Test
    public void normalizesToNFKC() {
        // Full-width forms should be converted to ASCII equivalents
        assertEquals("foobar123", User.normalizeUsername("ＦｏｏＢａｒ１２３"));
        // Ligatures/compatibility chars should be normalized
        assertEquals("office", User.normalizeUsername("o\uFB03ce")); // \uFB03 = "ffi" ligature
    }

    @Test
    public void preservesBasicAscii() {
        assertEquals("john.doe", User.normalizeUsername("john.doe"));
        assertEquals("john-doe_123", User.normalizeUsername("John-Doe_123").toLowerCase());
    }

    @Test
    public void normalizesEmailLikeUsernames() {
        assertEquals("eleve@example.com", User.normalizeUsername(" ÉlÉVe@ExAmPle.com "));
    }

    @Test
    public void isIdempotent() {
        String once = User.normalizeUsername("  ÉlÉVe@ExAmPle.com  ");
        String twice = User.normalizeUsername(once);
        assertEquals(once, twice);
    }

}
