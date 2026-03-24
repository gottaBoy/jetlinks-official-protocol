package org.jetlinks.protocol.official.parallel.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * AES 密钥消息 (aespub)
 * 设备 -> 云端
 */
@Data
public class AesKeyMessage {
    @JsonProperty("name")
    private String name = "aespub";

    @JsonProperty("vin")
    private String vin;

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type; // "veh" 表示车端向云端发送数据

    @JsonProperty("omsno")
    private String omsno;

    @JsonProperty("seq")
    private String seq;

    @JsonProperty("version")
    private String version;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("aes_key")
    private String aesKey; // RSA 加密后的 AES 密钥
}

