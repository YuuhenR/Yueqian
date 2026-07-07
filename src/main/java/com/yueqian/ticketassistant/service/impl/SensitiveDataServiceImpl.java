package com.yueqian.ticketassistant.service.impl;

import com.yueqian.ticketassistant.config.SecurityProperties;
import com.yueqian.ticketassistant.service.SensitiveDataService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class SensitiveDataServiceImpl implements SensitiveDataService {

    private static final String PREFIX = "enc:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SensitiveDataServiceImpl(SecurityProperties properties) {
        this.key = new SecretKeySpec(deriveKey(properties.getJwtSecret()), "AES");
    }

    @Override
    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.trim().getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv).put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("敏感信息加密失败", ex);
        }
    }

    @Override
    public String decrypt(String value) {
        if (!StringUtils.hasText(value) || !value.startsWith(PREFIX)) {
            return value;
        }
        try {
            byte[] packed = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(packed, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(packed, IV_LENGTH, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("敏感信息解密失败", ex);
        }
    }

    @Override
    public boolean matches(String plainText, String storedValue) {
        if (!StringUtils.hasText(plainText) || !StringUtils.hasText(storedValue)) {
            return false;
        }
        return plainText.trim().equals(decrypt(storedValue));
    }

    @Override
    public String maskIdCard(String value) {
        String plain = decrypt(value);
        if (!StringUtils.hasText(plain) || plain.length() < 8) {
            return "已脱敏";
        }
        return plain.substring(0, 4) + "**********" + plain.substring(plain.length() - 4);
    }

    private byte[] deriveKey(String secret) {
        try {
            return Arrays.copyOf(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)), 16);
        } catch (Exception ex) {
            throw new IllegalStateException("加密密钥初始化失败", ex);
        }
    }
}
