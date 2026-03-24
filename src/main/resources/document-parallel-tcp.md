# 平行驾驶协议 TCP 接入文档

## 概述

平行驾驶协议支持通过 TCP 方式接入平台，支持两种模式：

1. **加密模式**（可选）：采用 RSA+AES 混合加密方案进行密钥认证，认证完成后所有通信均使用 AES 加密
2. **非加密模式**（默认）：直接认证，所有消息以明文 JSON 格式传输

**加密开关默认关闭**，可在设备配置中开启。

## 认证流程

### 1. 设备发送认证请求 (authreq)

设备连接后，首先发送认证请求：

**车端示例：**
```json
{
    "name": "authreq",
    "vin": "MVXRCM1F2XLA00001",
    "id": "2d216da1-cbc6-11ea-b9ae-00163e02f3f9",
    "type": "veh",
    "omsno": "oms001",
    "seq": "1",
    "version": "1.0",
    "timestamp": "2025-07-22 10:49:26"
}
```

**手柄端示例：**
```json
{
    "name": "authreq",
    "vin": "JOY-G29-00001",
    "id": "2d216da1-cbc6-11ea-b9ae-00163e02f3f9",
    "type": "veh",
    "omsno": "oms001",
    "seq": "1",
    "version": "1.0",
    "timestamp": "2025-07-22 10:49:26"
}
```

**字段说明：**
- `type`: "veh" 表示车端向云端发送数据，"oms" 表示云端向车端/设备端发送数据
- `vin`: 车端格式为 `MVXRCM1F2XLA00001`，手柄端格式为 `JOY-G29-00001`

**消息格式：**
- 前 4 字节：消息长度（大端，INT32）
- 后续字节：JSON 字符串（UTF-8 编码）

### 2. 云端发送 RSA 公钥 (rsapub)

云端收到认证请求后，生成 RSA 密钥对，并发送公钥：

```json
{
    "name": "rsapub",
    "vin": "MVXRCM1F2XLA00001",
    "id": "2d216da1-cbc6-11ea-b9ae-00163e02f3f9",
    "type": "oms",
    "omsno": "oms001",
    "seq": "1",
    "version": "1.0",
    "timestamp": "2025-07-22 10:49:26",
    "rsapub_key": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."
}
```

### 3. 设备发送 AES 密钥 (aespub)

设备收到 RSA 公钥后，生成 AES 密钥，使用 RSA 公钥加密后发送：

```json
{
    "name": "aespub",
    "vin": "MVXRCM1F2XLA00001",
    "id": "2d216da1-cbc6-11ea-b9ae-00163e02f3f9",
    "type": "veh",
    "omsno": "oms001",
    "seq": "1",
    "version": "1.0",
    "timestamp": "2025-07-22 10:49:26",
    "aes_key": "RSA加密后的AES密钥Base64字符串"
}
```

### 4. 云端发送认证完成 (authover)

云端收到 AES 密钥后，使用 RSA 私钥解密，保存 AES 密钥，并发送认证完成消息：

```json
{
    "name": "authover",
    "vin": "MVXRCM1F2XLA00001",
    "id": "2d216da1-cbc6-11ea-b9ae-00163e02f3f9",
    "type": "oms",
    "omsno": "oms001",
    "seq": "1",
    "version": "1.0",
    "timestamp": "2025-07-22 10:49:26",
    "authresult": "ok"
}
```

**认证失败时：**
```json
{
    "name": "authover",
    ...
    "authresult": "错误信息"
}
```

## 认证超时

- 认证过程中任意一条指令接收超时 **2秒** 即认定认证失败
- 认证失败后需要重新开始认证流程

## 加密通信

### 加密开关

协议支持开启/关闭通信加密功能，默认**关闭**。

在设备或产品配置中设置：
- **启用加密**：是否启用通信加密（默认：false）

### 加密模式

当**启用加密**时，认证完成后，后续所有指令和应答都需要进行 **AES 加密**处理：

1. 将消息转换为 JSON 字符串
2. 使用 AES 密钥加密（ECB 模式，PKCS5Padding）
3. 将加密结果进行 Base64 编码
4. 发送格式：前 4 字节为长度，后续为加密后的 Base64 字符串

### 非加密模式

当**关闭加密**时：
- 认证流程简化，直接发送认证完成消息
- 所有消息以明文 JSON 格式传输
- 无需 RSA+AES 密钥交换

## 设备配置

在 JetLinks 平台中配置设备时，需要设置以下参数：

- **VIN码**：车辆 VIN 码或手柄端编号
  - 车端格式：`MVXRCM1F2XLA00001`
  - 手柄端格式：`JOY-G29-00001`
- **OMS编号**：平行驾驶设备 OMS 编号
- **启用加密**：是否启用通信加密（默认：false）

## 设备上线

认证完成后，平台会自动发送设备上线消息，设备状态变为在线。

## 设备绑定

云端打开平行驾驶 UI 界面，在平行驾驶设备处选择上线的平行驾驶设备，即可实现平行驾驶设备和车端的绑定。平行驾驶设备控制端即可接收到绑定的车辆 VIN 码。

## 示例代码

### Java 客户端示例

```java
// 1. 建立 TCP 连接
Socket socket = new Socket("iot.intra.zeron.ai", 8801);

// 2. 发送认证请求
AuthRequestMessage authReq = new AuthRequestMessage();
authReq.setVin("MVXRCM1F2XLA00001");
authReq.setId("device-001");
authReq.setType("obu");
authReq.setOmsno("oms001");
authReq.setSeq("1");
authReq.setVersion("1.0");
authReq.setTimestamp("2025-07-22 10:49:26");

String json = objectMapper.writeValueAsString(authReq);
sendMessage(socket, json);

// 3. 接收 RSA 公钥
RsaPublicKeyMessage rsaPub = receiveMessage(socket, RsaPublicKeyMessage.class);

// 4. 生成 AES 密钥并加密发送
String aesKey = ParallelCipher.generateAESKey();
String encryptedAesKey = ParallelCipher.rsaEncrypt(aesKey, rsaPub.getRsapubKey());

AesKeyMessage aesKeyMsg = new AesKeyMessage();
// ... 设置字段
aesKeyMsg.setAesKey(encryptedAesKey);
sendMessage(socket, objectMapper.writeValueAsString(aesKeyMsg));

// 5. 接收认证完成消息
AuthOverMessage authOver = receiveMessage(socket, AuthOverMessage.class);
if ("ok".equals(authOver.getAuthResult())) {
    // 认证成功，后续使用 AES 加密通信
    // ...
}
```

## 消息字段说明

### type 字段
- `"veh"`: 车端向云端发送数据（包括车端和手柄端）
- `"oms"`: 云端向车端/设备端发送数据

### vin 字段（VIN码格式）
- 车端：`MVXRCM1F2XLA00001`
- 手柄端：`JOY-G29-00001`

## 注意事项

1. **认证超时**：启用加密时，认证过程必须在 2 秒内完成，否则会失败
2. **加密要求**：启用加密后，认证完成后的所有消息必须使用 AES 加密
3. **消息格式**：所有消息都采用 JSON 格式，UTF-8 编码
4. **长度前缀**：TCP 消息前 4 字节为消息长度（大端，INT32）
5. **RSA 密钥长度**：使用 2048 位 RSA 密钥（仅加密模式）
6. **AES 密钥长度**：使用 128 位 AES 密钥（仅加密模式）
7. **加密开关**：默认关闭，可根据需要开启

