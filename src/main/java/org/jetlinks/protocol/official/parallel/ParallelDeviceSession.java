package org.jetlinks.protocol.official.parallel;

import lombok.Data;
import org.jetlinks.protocol.official.parallel.cipher.ParallelCipher;

import java.security.KeyPair;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 平行驾驶设备会话
 * 存储认证状态和加密密钥
 */
@Data
public class ParallelDeviceSession {
    private String deviceId;
    private String vin;
    private String omsno;
    
    // RSA 密钥对（云端生成）
    private KeyPair rsaKeyPair;
    
    // AES 密钥（设备生成，RSA 加密传输）
    private AtomicReference<String> aesKey = new AtomicReference<>();
    
    // 认证状态
    private AuthState authState = AuthState.NOT_STARTED;
    
    // 认证超时时间（2秒）
    private static final long AUTH_TIMEOUT_MS = 2000;
    private long authStartTime;
    
    public enum AuthState {
        NOT_STARTED,      // 未开始
        WAITING_RSA_PUB,  // 等待发送 RSA 公钥
        WAITING_AES_KEY,  // 等待接收 AES 密钥
        AUTHENTICATED     // 认证完成
    }
    
    public ParallelDeviceSession(String deviceId, String vin, String omsno) {
        this.deviceId = deviceId;
        this.vin = vin;
        this.omsno = omsno;
        // 生成 RSA 密钥对
        this.rsaKeyPair = ParallelCipher.generateRSAKeyPair();
    }
    
    public void startAuth() {
        this.authState = AuthState.WAITING_RSA_PUB;
        this.authStartTime = System.currentTimeMillis();
    }
    
    public boolean isAuthTimeout() {
        return System.currentTimeMillis() - authStartTime > AUTH_TIMEOUT_MS;
    }
    
    public String getRsaPublicKeyBase64() {
        return ParallelCipher.publicKeyToBase64(rsaKeyPair.getPublic());
    }
    
    public String getRsaPrivateKeyBase64() {
        return ParallelCipher.privateKeyToBase64(rsaKeyPair.getPrivate());
    }
    
    public boolean isAuthenticated() {
        return authState == AuthState.AUTHENTICATED && aesKey.get() != null;
    }
}

