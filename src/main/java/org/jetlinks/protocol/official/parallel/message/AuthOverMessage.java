package org.jetlinks.protocol.official.parallel.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 认证完成消息 (authover)
 * 云端 -> 设备
 */
@Data
public class AuthOverMessage {
    @JsonProperty("name")
    private String name = "authover";

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

    @JsonProperty("authresult")
    private String authResult; // "ok" 或错误信息
}

