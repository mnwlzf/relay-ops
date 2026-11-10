package com.relayops.upstream.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relayops.shared.error.BusinessException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CredentialCipherTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void roundTripsWithoutExposingPlaintext() {
        CredentialCipher cipher = new CredentialCipher(KEY);

        String encrypted = cipher.encrypt("sk-very-secret-token");

        assertThat(encrypted).startsWith("v1:").doesNotContain("very-secret");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("sk-very-secret-token");
        // same plaintext produces a different ciphertext because of the random IV
        assertThat(cipher.encrypt("sk-very-secret-token")).isNotEqualTo(encrypted);
    }

    @Test
    void refusesToWorkWithoutAKey() {
        CredentialCipher cipher = new CredentialCipher("");

        assertThat(cipher.available()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt("sk-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RELAY_OPS_CREDENTIAL_KEY");
    }

    @Test
    void failsLoudlyWhenTheKeyChanged() {
        String encrypted = new CredentialCipher(KEY).encrypt("sk-1");
        String otherKey = Base64.getEncoder()
                .encodeToString("ffffffffffffffffffffffffffffffff".getBytes());
        CredentialCipher other = new CredentialCipher(otherKey);

        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    void masksCredentialsForDisplay() {
        assertThat(CredentialCipher.hint("sk-abcdef123456")).isEqualTo("sk****56");
        assertThat(CredentialCipher.hint("short")).isEqualTo("****");
        assertThat(CredentialCipher.hint(null)).isEqualTo("****");
    }
}
