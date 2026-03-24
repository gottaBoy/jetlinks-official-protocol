# 平行驾驶协议包

## 概述

平行驾驶协议包实现了平行驾驶设备的 TCP 接入，支持 RSA+AES 混合加密认证和加密通信。

## 功能特性

- ✅ **可选加密**：支持开启/关闭通信加密（默认关闭）
- ✅ RSA+AES 混合加密认证（加密模式）
- ✅ 2秒认证超时控制（加密模式）
- ✅ 认证完成后 AES 加密通信（加密模式）
- ✅ 设备上线和绑定
- ✅ VIN 码和 OMS 编号验证
- ✅ 支持车端和手柄端两种终端类型
- ✅ 支持数据流向标识（veh/oms）

## 认证流程

1. **设备发送认证请求** (authreq)
2. **云端发送 RSA 公钥** (rsapub)
3. **设备发送 AES 密钥** (aespub) - RSA 加密
4. **云端发送认证完成** (authover)

详细流程请参考：[document-parallel-tcp.md](src/main/resources/document-parallel-tcp.md)

## 编译和打包

```bash
mvn clean package
```

打包后的 JAR 文件位于 `target/` 目录。

## 安装协议包

1. 将打包好的 JAR 文件上传到 JetLinks 平台
2. 在协议管理中创建新协议，选择协议包
3. 配置协议 ID 为 `parallel-driving`

## 设备配置

在设备或产品配置中设置：

- **VIN码**：车辆 VIN 码或手柄端编号（必填）
  - 车端格式：`MVXRCM1F2XLA00001`
  - 手柄端格式：`JOY-G29-00001`
- **OMS编号**：平行驾驶设备 OMS 编号（必填）
- **启用加密**：是否启用通信加密（默认：false）

## 使用示例

### 设备端连接示例

```java
// 1. 建立 TCP 连接
Socket socket = new Socket("iot.intra.zeron.ai", 8889);

// 2. 发送认证请求
AuthRequestMessage authReq = new AuthRequestMessage();
authReq.setVin("MVXRCM1F2XLA00001");
authReq.setId("device-001");
authReq.setType("obu");
authReq.setOmsno("oms001");
// ... 设置其他字段

// 3. 接收 RSA 公钥并发送 AES 密钥
// 4. 接收认证完成消息
// 5. 后续使用 AES 加密通信
```

详细示例请参考文档。

## 注意事项

1. **加密开关**：默认关闭，可根据需要开启
2. **认证超时**：启用加密时，认证过程必须在 2 秒内完成
3. **加密要求**：启用加密后，认证完成后的所有消息必须使用 AES 加密
4. **消息格式**：前 4 字节为长度，后续为 JSON 字符串（或加密后的 Base64 字符串）
5. **终端类型**：支持 vehicle（车端）和 joystick（手柄端）
6. **数据流向**：type 字段标识数据流向（veh/oms）

## 技术支持

如有问题，请查看日志或联系技术支持。

