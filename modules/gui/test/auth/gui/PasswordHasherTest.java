package auth.gui;

import org.junit.Test;
import utils.common.HashUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class PasswordHasherTest {

    @Test
    public void saltsAreUniqueAndHashesFitExistingColumn() {
        String password = "pässword 🔐";
        String first = PasswordHasher.hash(password);
        String second = PasswordHasher.hash(password);
        assertThat(first).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(first.length()).isLessThanOrEqualTo(255);
        assertThat(first).isNotEqualTo(second);
        assertThat(PasswordHasher.verify(password, first)).isTrue();
        assertThat(PasswordHasher.verify(password, second)).isTrue();
        assertThat(PasswordHasher.verify("wrong", first)).isFalse();
    }

    @Test
    public void legacyHashesRemainVerifiable() {
        String legacy = HashUtils.getHashMD5("pässword");
        assertThat(PasswordHasher.isLegacy(legacy)).isTrue();
        assertThat(PasswordHasher.verify("pässword", legacy)).isTrue();
        assertThat(PasswordHasher.verify("wrong", legacy)).isFalse();
    }

    @Test
    public void missingMalformedAndUnknownHashesFailClosed() {
        for (String hash : new String[] {null, "", "not-a-hash", "a".repeat(31),
                "$argon2id$broken", "$argon2i$v=19$m=19456,t=2,p=1$abc$def"}) {
            assertThat(PasswordHasher.verify("secret", hash)).isFalse();
        }
        assertThat(PasswordHasher.verify(null, HashUtils.getHashMD5("secret"))).isFalse();
    }
}
