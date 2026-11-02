package utils.common;

import general.common.Common;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Base64;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;

public class GitCredentialEncryptionTest {

    private GitCredentialEncryption encryption(String secret) {
        try (MockedStatic<Common> common = mockStatic(Common.class)) {
            common.when(Common::getApplicationSecret).thenReturn(secret);
            return new GitCredentialEncryption();
        }
    }

    @Test
    public void roundTripsUsesFreshNoncesAndBindsContext() {
        GitCredentialEncryption crypto = encryption("stable-random-application-secret-for-testing");
        String secret = "token-with-unicode-ü-and-spaces ";
        String first = crypto.encrypt(secret, "owner/origin/user");
        String second = crypto.encrypt(secret, "owner/origin/user");
        assertNotEquals(first, second);
        assertFalse(first.contains(secret));
        assertEquals(secret, crypto.decrypt(first, "owner/origin/user"));
        assertEquals(secret, crypto.decrypt(second, "owner/origin/user"));
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(first, "different-owner"));
        assertThrows(IllegalStateException.class, () -> encryption("different-random-application-secret-for-testing")
                .decrypt(first, "owner/origin/user"));
    }

    @Test
    public void rejectsTamperingMalformedDataAndUnknownVersionsWithoutEchoingInput() {
        GitCredentialEncryption crypto = encryption("stable-random-application-secret-for-testing");
        String original = crypto.encrypt("private-token", "context");
        String[] parts = original.split(":");
        byte[] bytes = Base64.getDecoder().decode(parts[2]);
        bytes[0] ^= 1;
        String modifiedCiphertext = parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(bytes);
        byte[] nonce = Base64.getDecoder().decode(parts[1]);
        nonce[0] ^= 1;
        String modifiedNonce = parts[0] + ":" + Base64.getEncoder().encodeToString(nonce) + ":" + parts[2];
        for (String value : new String[]{null, "", "private-token", "v2:" + parts[1] + ":" + parts[2],
                "v1:not-base64!:x", "v1:AA==:AA==", modifiedCiphertext, modifiedNonce}) {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> crypto.decrypt(value, "context"));
            assertEquals("Stored Git credentials cannot be decrypted. Replace them with a password or token.", error.getMessage());
            assertNull(error.getCause());
        }
    }

    @Test
    public void rejectsMissingAndDefaultApplicationSecrets() {
        assertThrows(IllegalStateException.class, () -> encryption(null));
        assertThrows(IllegalStateException.class, () -> encryption("changeme"));
        assertThrows(IllegalStateException.class, () -> encryption("short"));
    }
}
