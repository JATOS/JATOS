package auth.gui;

import com.password4j.Argon2Function;
import com.password4j.types.Argon2;
import utils.common.HashUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Password hashing with a fixed Argon2id policy and legacy MD5 verification.
 */
public final class PasswordHasher {

    private static final int MEMORY_KIB = 19456;
    private static final int ITERATIONS = 2;
    private static final int PARALLELISM = 1;
    private static final int HASH_BYTES = 32;
    private static final int SALT_BYTES = 16;
    private static final int ARGON2_VERSION = 19;
    // Base64 lengths without padding.
    private static final int SALT_BASE64_LENGTH = (SALT_BYTES * 8 + 5) / 6;
    private static final int HASH_BASE64_LENGTH = (HASH_BYTES * 8 + 5) / 6;

    private static final Argon2Function ARGON2 = Argon2Function.getInstance(
            MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_BYTES, Argon2.ID, ARGON2_VERSION);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern LEGACY_MD5_PATTERN = Pattern.compile("[0-9a-f]{32}");
    // Accept only our bounded policy and encoding; malformed hashes fail closed.
    private static final Pattern ARGON2ID_PATTERN = Pattern.compile(
            "\\$argon2id\\$v=" + ARGON2_VERSION
                    + "\\$m=" + MEMORY_KIB
                    + ",t=" + ITERATIONS
                    + ",p=" + PARALLELISM
                    + "\\$[A-Za-z0-9+/]{" + SALT_BASE64_LENGTH + "}"
                    + "\\$[A-Za-z0-9+/]{" + HASH_BASE64_LENGTH + "}");

    /**
     * Prevents instantiation of this utility class.
     */
    private PasswordHasher() {
    }

    /**
     * Creates an encoded Argon2id hash with a fresh random salt.
     */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return ARGON2.hash(password.getBytes(StandardCharsets.UTF_8), salt).getResult();
    }

    /**
     * Recognizes the legacy lowercase MD5 format for migration.
     */
    public static boolean isLegacy(String storedHash) {
        return storedHash != null && LEGACY_MD5_PATTERN.matcher(storedHash).matches();
    }

    /**
     * Verifies Argon2id or legacy MD5 hashes; rejects missing or unsupported formats.
     */
    public static boolean verify(String password, String storedHash) {
        if (password == null || storedHash == null) return false;
        if (isLegacy(storedHash)) {
            return MessageDigest.isEqual(
                    HashUtils.getHashMD5(password).getBytes(StandardCharsets.US_ASCII),
                    storedHash.getBytes(StandardCharsets.US_ASCII));
        }
        if (!ARGON2ID_PATTERN.matcher(storedHash).matches()) return false;
        return ARGON2.check(password, storedHash);
    }
}
