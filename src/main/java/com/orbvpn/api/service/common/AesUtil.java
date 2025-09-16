package com.orbvpn.api.service.common;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
public class AesUtil {
    public static final String KEY_GENERATION_ALGORITHM = "AES";
    public static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";
    public static final Integer KEY_LENGTH_IN_BIT = 256;
    public static final Integer INITIAL_VECTOR_SIZE_IN_BYTE = 16;

    // Generate the AES key
    public static String generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KEY_GENERATION_ALGORITHM);
            keyGenerator.init(KEY_LENGTH_IN_BIT);
            SecretKey key = keyGenerator.generateKey();
            return convertSecretKeyToString(key);
        } catch (NoSuchAlgorithmException e) {
            log.error("unsupported encryption algorithm", e);
            return null;
        }
    }

    // Generate Initialization Vector
    public static IvParameterSpec generateIv() {
        byte[] iv = new byte[INITIAL_VECTOR_SIZE_IN_BYTE];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    // Encrypt data
    public static String encrypt(String input, String key, String iv)
            throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException {
        IvParameterSpec ivParameterSpec = convertStringToIvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, convertStringToSecretKey(key), ivParameterSpec);
        byte[] cipherText = cipher.doFinal(input.getBytes());
        return Base64.getEncoder().encodeToString(cipherText);
    }

    // Decrypt data
    public static String decrypt(String cipherText, String key, String iv)
            throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException {
        IvParameterSpec ivParameterSpec = convertStringToIvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, convertStringToSecretKey(key), ivParameterSpec);
        byte[] plainText = cipher.doFinal(Base64.getDecoder().decode(cipherText));
        return new String(plainText);
    }

    public static String convertSecretKeyToString(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static SecretKey convertStringToSecretKey(String encodedKey) {
        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, KEY_GENERATION_ALGORITHM);
    }

    public static String convertIvParameterSpecToString(IvParameterSpec ivParameterSpec) {
        return Base64.getEncoder().encodeToString(ivParameterSpec.getIV());
    }

    public static IvParameterSpec convertStringToIvParameterSpec(String ivStr) {
        byte[] decodedIv = Base64.getDecoder().decode(ivStr);
        return new IvParameterSpec(decodedIv);
    }
}