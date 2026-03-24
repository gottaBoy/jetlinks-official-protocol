package org.jetlinks.protocol.official.parallel.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * RSA 公钥消息 (rsapub)
 * 云端 -> 设备
 */
@Data
public class RsaPublicKeyMessage {
    @JsonProperty("name")
    private String name = "rsapub";

    @JsonProperty("vin")
    private String vin;

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type; // "oms" 表示云端向车端/设备端发送数据

    @JsonProperty("omsno")
    private String omsno;

    @JsonProperty("seq")
    private String seq;

    @JsonProperty("version")
    private String version;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("rsapub_key")
    private String rsapubKey;
}

