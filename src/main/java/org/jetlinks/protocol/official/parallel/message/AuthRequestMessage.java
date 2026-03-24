package org.jetlinks.protocol.official.parallel.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 认证请求消息 (authreq)
 * 设备 -> 云端
 */
@Data
public class AuthRequestMessage {
    @JsonProperty("name")
    private String name = "authreq";

    @JsonProperty("vin")
    private String vin;

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String type; // "veh" 表示车端向云端发送数据, "oms" 表示云端向车端/设备端发送数据

    @JsonProperty("omsno")
    private String omsno;

    @JsonProperty("seq")
    private String seq;

    @JsonProperty("version")
    private String version;

    @JsonProperty("timestamp")
    private String timestamp;
}

