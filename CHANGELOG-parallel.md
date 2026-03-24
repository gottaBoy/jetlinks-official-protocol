# 平行驾驶协议更新日志

## 最新更新

### 1. 添加通信加密开关 ✅

- **默认关闭加密**：协议默认不启用加密，所有消息以明文 JSON 格式传输
- **可配置开关**：在设备或产品配置中可以开启/关闭加密功能
- **配置项**：`enableEncrypt`（布尔类型，默认：false）

### 2. 更新消息格式 ✅

#### type 字段含义更新
- `"veh"`: 车端向云端发送数据
- `"oms"`: 云端向车端/设备端发送数据


#### VIN 码格式
- **车端格式**：`MVXRCM1F2XLA00001`
- **手柄端格式**：`JOY-G29-00001`

### 3. 消息字段说明

所有消息都包含以下基础字段：

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| name | String | 消息名称 | "authreq", "rsapub", "aespub", "authover" |
| vin | String | 设备VIN码，唯一编码 | "MVXRCM1F2XLA00001" 或 "JOY-G29-00001" |
| id | String | 消息uuid，每条消息独有 | "2d216da1-cbc6-11ea-b9ae-00163e02f3f9" |
| type | String | 数据流向 | "veh" 或 "oms" |
| omsno | String | 云端服务器平台编号 | "oms001" |
| seq | String | 消息序号（默认值为1） | "1" |
| version | String | 当前消息格式版本 | "1.0" |
| timestamp | String | 时间戳，精确到秒 | "2025-07-22 10:49:26" |

## 使用方式

### 加密模式（启用加密）

1. 在设备配置中设置 `enableEncrypt = true`
2. 设备连接后执行完整的 RSA+AES 认证流程
3. 认证完成后所有消息使用 AES 加密

### 非加密模式（默认，关闭加密）

1. 设备配置中 `enableEncrypt = false`（默认值）
2. 设备连接后直接发送认证请求
3. 云端验证 VIN 和 OMS 编号后直接上线
4. 所有消息以明文 JSON 格式传输

## 兼容性

- ✅ 向后兼容：默认关闭加密，不影响现有设备
- ✅ 可选加密：可根据需要开启加密功能
- ✅ 灵活配置：每个设备可独立配置加密开关

## 示例

### 车端认证请求（非加密模式）

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

### 手柄端认证请求（非加密模式）

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

