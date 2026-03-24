# 平行驾驶协议集成方案

## 方案对比

### 方案 1: 独立协议包（当前实现）✅ 推荐

**优点：**
- ✅ 协议独立，不影响官方协议
- ✅ 易于维护和升级
- ✅ 可以单独打包和部署
- ✅ 协议ID清晰：`parallel-driving`

**实现方式：**
- 已创建 `ParallelProtocolSupportProvider`
- 已通过 SPI 文件注册
- **无需修改** `JetLinksProtocolSupportProvider`

**使用方式：**
- 在 JetLinks 平台中创建协议，选择协议包
- 协议ID会自动识别为 `parallel-driving`

---

### 方案 2: 集成到官方协议（可选）

如果希望平行驾驶协议作为官方协议的一部分，可以在 `JetLinksProtocolSupportProvider` 中添加：

```java
// 在 JetLinksProtocolSupportProvider.java 中添加
import org.jetlinks.protocol.official.parallel.tcp.ParallelTcpDeviceMessageCodec;

// 在 create 方法中添加
// 平行驾驶 TCP 支持（作为官方协议的一部分）
support.addConfigMetadata(DefaultTransport.TCP, ParallelTcpDeviceMessageCodec.tcpConfig);
support.addMessageCodecSupport(new ParallelTcpDeviceMessageCodec(context));
```

**优点：**
- ✅ 所有协议在一个包中
- ✅ 统一管理

**缺点：**
- ❌ 协议ID会变成 `jetlinks.v3.2`（而不是 `parallel-driving`）
- ❌ 与官方协议耦合
- ❌ 升级官方协议时可能受影响

---

## 推荐方案

**推荐使用方案 1（独立协议包）**，因为：
1. 协议职责清晰
2. 易于维护和升级
3. 不影响官方协议
4. 可以独立版本控制

## 当前配置

当前已实现**方案 1**，SPI 配置文件已创建：
- `src/main/resources/META-INF/services/org.jetlinks.core.spi.ProtocolSupportProvider`

包含两个协议提供者：
1. `org.jetlinks.protocol.official.JetLinksProtocolSupportProvider` - 官方协议
2. `org.jetlinks.protocol.official.parallel.ParallelProtocolSupportProvider` - 平行驾驶协议

**无需修改 `JetLinksProtocolSupportProvider.java`**

