package org.jetlinks.protocol.official.parallel.cipher;

import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * 平行驾驶协议加密工具类
 * 支持 RSA + AES 混合加密
 */
public class ParallelCipher {

    /**
     * 生成 RSA 密钥对
     */
    @SneakyThrows
    public static KeyPair generateRSAKeyPair() {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * 生成 AES 密钥
     */
    @SneakyThrows
    public static String generateAESKey() {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128);
        SecretKey secretKey = keyGenerator.generateKey();
        return Base64.encodeBase64String(secretKey.getEncoded());
    }

    /**
     * RSA 公钥加密
     */
    @SneakyThrows
    public static String rsaEncrypt(String data, String publicKeyBase64) {
        byte[] publicKeyBytes = Base64.decodeBase64(publicKeyBase64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(spec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(data.getBytes("UTF-8"));
        return Base64.encodeBase64String(encrypted);
    }

    /**
     * RSA 私钥解密
     */
    @SneakyThrows
    public static String rsaDecrypt(String encryptedData, String privateKeyBase64) {
        byte[] privateKeyBytes = Base64.decodeBase64(privateKeyBase64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(spec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decrypted = cipher.doFinal(Base64.decodeBase64(encryptedData));
        return new String(decrypted, "UTF-8");
    }

    /**
     * AES 加密
     */
    @SneakyThrows
    public static String aesEncrypt(String data, String keyBase64) {
        byte[] keyBytes = Base64.decodeBase64(keyBase64);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encrypted = cipher.doFinal(data.getBytes("UTF-8"));
        return Base64.encodeBase64String(encrypted);
    }

    /**
     * AES 解密
     */
    @SneakyThrows
    public static String aesDecrypt(String encryptedData, String keyBase64) {
        byte[] keyBytes = Base64.decodeBase64(keyBase64);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decrypted = cipher.doFinal(Base64.decodeBase64(encryptedData));
        return new String(decrypted, "UTF-8");
    }

    /**
     * 将公钥转换为 Base64 字符串
     */
    public static String publicKeyToBase64(PublicKey publicKey) {
        return Base64.encodeBase64String(publicKey.getEncoded());
    }

    /**
     * 将私钥转换为 Base64 字符串
     */
    public static String privateKeyToBase64(PrivateKey privateKey) {
        return Base64.encodeBase64String(privateKey.getEncoded());
    }
}

