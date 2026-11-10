package com.relayops.upstream.application.security;

import com.relayops.shared.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-GCM encryption for upstream credentials.
 *
 * <p>The key comes from {@code RELAY_OPS_CREDENTIAL_KEY} (base64, 16/24/32 bytes). Without a key the
 * application still starts, but storing a credential fails loudly instead of writing plaintext.</p>
 */
@Component
public class CredentialCipher {

    private static final Logger log = LoggerFactory.getLogger(CredentialCipher.class);
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final String PREFIX = "v1:";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CredentialCipher(@Value("${relay-ops.upstream.credential-key:}") String base64Key) {
        this.key = parseKey(base64Key);
        if (key == null) {
            log.warn("未配置 RELAY_OPS_CREDENTIAL_KEY，账号凭证将无法保存（也不会以明文落库）");
        }
    }

    public boolean available() {
        return key != null;
    }

    /** @return {@code v1:<base64(iv||ciphertext)>} */
    public String encrypt(String plaintext) {
        if (key == null) {
            throw new BusinessException("CREDENTIAL_KEY_MISSING",
                    "未配置 RELAY_OPS_CREDENTIAL_KEY，无法保存账号凭证");
        }
        if (plaintext == null || plaintext.isBlank()) {
            throw new BusinessException("CREDENTIAL_EMPTY", "凭证不能为空");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new BusinessException("CREDENTIAL_ENCRYPT_FAILED", "凭证加密失败", ex);
        }
    }

    public String decrypt(String stored) {
        if (key == null) {
            throw new BusinessException("CREDENTIAL_KEY_MISSING",
                    "未配置 RELAY_OPS_CREDENTIAL_KEY，无法读取账号凭证");
        }
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new BusinessException("CREDENTIAL_FORMAT_INVALID", "凭证格式不合法");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BusinessException("CREDENTIAL_DECRYPT_FAILED", "凭证解密失败（密钥可能已更换）", ex);
        }
    }

    /** Masks a credential for display: keeps the first two and last two characters. */
    public static String hint(String plaintext) {
        if (plaintext == null || plaintext.length() <= 6) {
            return "****";
        }
        return plaintext.substring(0, 2) + "****" + plaintext.substring(plaintext.length() - 2);
    }

    private static SecretKeySpec parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Key.strip());
            if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
                log.warn("RELAY_OPS_CREDENTIAL_KEY 长度不合法（需 16/24/32 字节），凭证功能已禁用");
                return null;
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException ex) {
            log.warn("RELAY_OPS_CREDENTIAL_KEY 不是合法 base64，凭证功能已禁用");
            return null;
        }
    }
}
