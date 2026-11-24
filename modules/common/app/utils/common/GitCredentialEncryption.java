package utils.common;

import general.common.Common;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts Git credentials; v1 fixes both the key derivation and ciphertext format.
 */
@Singleton
public class GitCredentialEncryption {

    private static final byte[] KEY_PURPOSE = "jatos-git-credentials-v1".getBytes(StandardCharsets.UTF_8);
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    @Inject
    public GitCredentialEncryption() {
        String secret = Common.getApplicationSecret();
        if (secret == null) {
            throw new IllegalStateException("Configure a stable JATOS secret before storing Git credentials.");
        }
        if (secret.equals("changeme") || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Git credentials require a stable JATOS secret of at least 32 bytes.");
        }

        // The application secret is already random. A fixed purpose-specific salt separates this key from
        // Play's signing key. Derive once per application instance, not on each encryption/decryption.
        char[] chars = secret.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, KEY_PURPOSE, 210_000, 256);
        try {
            byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try {key = new SecretKeySpec(derived, "AES");} finally {Arrays.fill(derived, (byte) 0);}
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Git credential encryption is unavailable.", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }

    /**
     * Context binds the ciphertext to its owner, origin and username; it is authenticated, not stored here.
     */
    public String encrypt(String secret, String context) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] plaintext = secret.getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, context);
            // Java appends the authentication tag to the ciphertext.
            return "v1:" + Base64.getEncoder().encodeToString(nonce) + ":"
                    + Base64.getEncoder().encodeToString(cipher.doFinal(plaintext));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Git credential encryption failed.", e);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    /**
     * Fails closed for malformed values, unknown versions, changed context, or a changed application secret.
     */
    public String decrypt(String encrypted, String context) {
        try {
            String[] parts = encrypted == null ? new String[0] : encrypted.split(":", -1);
            if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
            if (nonce.length != NONCE_BYTES || ciphertext.length < TAG_BITS / 8) throw new IllegalArgumentException();
            byte[] plaintext = cipher(Cipher.DECRYPT_MODE, nonce, context).doFinal(ciphertext);
            try {return new String(plaintext, StandardCharsets.UTF_8);} finally {Arrays.fill(plaintext, (byte) 0);}
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Do not include input or provider exception details in a user-facing error.
            throw new IllegalStateException("Stored Git credentials cannot be decrypted. Replace them with a password or token.");
        }
    }

    private Cipher cipher(int mode, byte[] nonce, String context) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(("v1\0" + context).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
