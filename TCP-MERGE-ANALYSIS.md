# TCP 协议合并分析

## 当前情况

### 1. JetLinksProtocolSupportProvider (官方协议)
- **协议ID**: `jetlinks.v3.2`
- **TCP编解码器**: `TcpDeviceMessageCodec`
- **消息格式**: 二进制格式（BinaryMessageType）
- **认证方式**: `secureKey` 密钥认证
- **配置项**: `secureKey` (PasswordType)
- **消息结构**: 
  - 前4字节：消息索引（index）
  - 后续：二进制消息体

### 2. ParallelProtocolSupportProvider (平行驾驶协议)
- **协议ID**: `parallel-driving`
- **TCP编解码器**: `ParallelTcpDeviceMessageCodec`
- **消息格式**: JSON格式
- **认证方式**: RSA+AES密钥认证（可选）
- **配置项**: `vin`, `omsno`, `enableEncrypt`
- **消息结构**:
  - 前4字节：消息长度（大端，INT32）
  - 后续：JSON字符串（UTF-8）或加密后的Base64字符串

## 差异对比

| 特性 | TcpDeviceMessageCodec | ParallelTcpDeviceMessageCodec |
|------|----------------------|------------------------------|
| 消息格式 | 二进制 | JSON |
| 认证方式 | secureKey | RSA+AES（可选） |
| 配置项 | secureKey | vin, omsno, enableEncrypt |
| 消息前缀 | index (4字节) | length (4字节) |
| 业务场景 | 通用TCP设备 | 平行驾驶专用 |

## 合并可行性分析

### ❌ 方案1: 直接合并（不推荐）

**问题**:
- 两个编解码器都使用 `DefaultTransport.TCP`
- 如果同时注册，JetLinks 可能无法正确选择使用哪个
- 配置项冲突（secureKey vs vin/omsno）
- 消息格式完全不同，无法兼容

### ✅ 方案2: 创建智能编解码器（推荐）

创建一个统一的 TCP 编解码器，根据消息格式自动选择处理方式：

```java
public class UnifiedTcpDeviceMessageCodec extends BlockingDeviceMessageCodec {
    
    @Override
    protected void upstream(BlockingMessageDecodeContext context) {
        ByteBuf payload = context.getData().getPayload();
        
        // 检测消息格式
        if (isJsonFormat(payload)) {
            // 使用 ParallelTcpDeviceMessageCodec 逻辑
            handleParallelProtocol(context, payload);
        } else {
            // 使用 TcpDeviceMessageCodec 逻辑
            handleOfficialProtocol(context, payload);
        }
    }
    
    private boolean isJsonFormat(ByteBuf payload) {
        // 检测是否为JSON格式
        // 1. 读取长度字段
        // 2. 尝试解析为JSON
        // 3. 如果成功，则是平行驾驶协议
    }
}
```

**优点**:
- ✅ 保留双方能力
- ✅ 自动识别消息格式
- ✅ 统一配置管理

**缺点**:
- ❌ 代码复杂度增加
- ❌ 需要维护两套逻辑
- ❌ 性能略有影响（需要检测消息格式）

### ✅ 方案3: 保持独立协议（最推荐）

**理由**:
1. **职责清晰**: 两个协议服务于不同的业务场景
2. **易于维护**: 各自独立，互不影响
3. **配置简单**: 用户根据设备类型选择对应协议
4. **性能最优**: 无需格式检测，直接处理

**使用方式**:
- 通用TCP设备 → 使用 `jetlinks.v3.2` 协议
- 平行驾驶设备 → 使用 `parallel-driving` 协议

## 推荐方案

### 🎯 推荐：保持独立协议（方案3）

**原因**:
1. 两个协议的业务场景完全不同
2. 消息格式差异太大（二进制 vs JSON）
3. 认证方式不同（secureKey vs RSA+AES）
4. 配置项不兼容
5. 独立协议更易于维护和升级

**如果确实需要合并，建议使用方案2（智能编解码器）**，但需要：
- 实现消息格式自动检测
- 合并配置元数据
- 处理两种认证流程
- 确保向后兼容

## 配置合并示例（如果采用方案2）

```java
public static final DefaultConfigMetadata tcpConfig = new DefaultConfigMetadata(
    "TCP认证配置", "")
    // 官方协议配置
    .add(CONFIG_KEY_SECURE_KEY.getKey(), "secureKey", "密钥", new PasswordType())
    // 平行驾驶协议配置
    .add(CONFIG_KEY_VIN.getKey(), "VIN码", "车辆VIN码或手柄端编号", new StringType())
    .add(CONFIG_KEY_OMS_NO.getKey(), "OMS编号", "平行驾驶设备OMS编号", new StringType())
    .add(CONFIG_KEY_ENABLE_ENCRYPT.getKey(), "启用加密", "是否启用通信加密", new BooleanType());
```

## 结论

**建议保持两个独立的协议**，因为：
- ✅ 职责清晰，易于维护
- ✅ 配置简单，用户友好
- ✅ 性能最优，无需检测
- ✅ 互不影响，独立升级

如果业务确实需要合并，可以采用智能编解码器方案，但会增加代码复杂度和维护成本。

