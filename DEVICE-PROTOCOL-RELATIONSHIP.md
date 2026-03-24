# JetLinks 设备与协议关系说明

## 核心概念

### 1. 协议层级

JetLinks 中的协议分为两个层级：

```
┌─────────────────────────────────────┐
│  消息协议 (Message Protocol)        │  ← 定义消息格式、编解码规则
│  例如: jetlinks.v3.2, parallel-driving │
└──────────────┬──────────────────────┘
               │ 可以支持多种传输协议
               ▼
┌─────────────────────────────────────┐
│  传输协议 (Transport Protocol)       │  ← 定义网络传输方式
│  例如: TCP, MQTT, HTTP, UDP, WebSocket │
└─────────────────────────────────────┘
```

### 2. 产品与设备的关系

```
产品 (Product)
  ├── messageProtocol: "jetlinks.v3.2"  ← 一个产品只能配置一个消息协议
  ├── transportProtocol: "TCP"          ← 一个产品只能配置一个传输协议
  └── 设备 (Device)
        ├── productId: "product-001"    ← 设备关联到产品
        └── 继承产品的协议配置
```

## 问题：一个设备是否可以支持多种协议？

### ❌ 答案：一个设备只能使用一个消息协议

**原因**：
1. **产品级别限制**：`DeviceProductEntity` 中的 `messageProtocol` 是单个字符串字段
2. **设备继承产品配置**：设备通过 `productId` 关联到产品，继承产品的协议配置
3. **架构设计**：JetLinks 采用"产品 → 设备"的层级结构，一个产品对应一个协议

### ✅ 但是：一个协议可以支持多种传输协议

**示例**：`jetlinks.v3.2` 协议支持：
- TCP
- MQTT
- HTTP
- UDP
- WebSocket
- CoAP

**使用方式**：
- 创建产品时，选择消息协议 `jetlinks.v3.2`
- 选择传输协议（如 TCP 或 MQTT）
- 设备继承产品的协议配置

## 实际场景

### 场景 1: 同一物理设备使用不同协议

**需求**：一个物理设备既要用 `jetlinks.v3.2`（TCP），又要用 `parallel-driving`（TCP）

**重要说明**：
- ❌ **设备ID是全局唯一的**（数据库主键），不能重复
- ❌ **如果产品不一样，设备ID也不可能一样**（数据库主键冲突）
- ✅ **需要使用不同的设备ID**

**解决方案**：
1. **创建两个产品**：
   - 产品A：`messageProtocol = "jetlinks.v3.2"`, `transportProtocol = "TCP"`
   - 产品B：`messageProtocol = "parallel-driving"`, `transportProtocol = "TCP"`

2. **创建两个设备实例**（必须使用不同的设备ID）：
   - 设备A：`productId = "产品A"`, `deviceId = "device-001-tcp"`
   - 设备B：`productId = "产品B"`, `deviceId = "device-001-parallel"`（不同设备ID）

3. **通过不同的网关接入**：
   - 网关1：使用 `jetlinks.v3.2` 协议，设备A接入
   - 网关2：使用 `parallel-driving` 协议，设备B接入

**注意**：
- 这是两个不同的设备实例（不同的设备ID）
- 虽然代表同一个物理设备，但在 JetLinks 中是两个独立的设备
- 数据、状态、配置都是独立的

### 场景 2: 同一协议使用不同传输方式

**需求**：一个设备使用 `jetlinks.v3.2` 协议，但有时用 TCP，有时用 MQTT

**解决方案**：
1. **创建两个产品**（使用相同的消息协议，不同的传输协议）：
   - 产品A：`messageProtocol = "jetlinks.v3.2"`, `transportProtocol = "TCP"`
   - 产品B：`messageProtocol = "jetlinks.v3.2"`, `transportProtocol = "MQTT"`

2. **创建两个设备实例**：
   - 设备A：`productId = "产品A"`
   - 设备B：`productId = "产品B"`

3. **通过不同的网关接入**：
   - TCP网关：设备A接入
   - MQTT网关：设备B接入

## 代码验证

### 产品实体

```java
// DeviceProductEntity.java
@Column(name = "message_protocol")
@Schema(description = "消息协议ID")
private String messageProtocol;  // ← 单个字符串，只能配置一个协议

@Column(name = "transport_protocol")
@Schema(description = "传输协议")
private String transportProtocol;  // ← 单个字符串，只能配置一个传输协议
```

### 设备实体

```java
// DeviceInstanceEntity.java
@Column(name = "product_id", length = 64, updatable = false)
@Schema(description = "产品ID")
private String productId;  // ← 设备关联到产品，继承产品的协议配置
```

### 协议获取逻辑

```java
// DefaultDeviceConfigMetadataSupplier.java
protected <T> Mono<T> computeDeviceProtocol(String productId, ...) {
    return productService
        .createQuery()
        .select(DeviceProductEntity::getMessageProtocol, 
                DeviceProductEntity::getTransportProtocol)
        .where(DeviceProductEntity::getId, productId)
        .fetchOne()
        .flatMap(product -> {
            // 从产品中获取协议配置
            Mono.justOrEmpty(product.getMessageProtocol())
                .flatMap(protocolSupports::getProtocol)
            ...
        });
}
```

## 设备ID唯一性说明

### ⚠️ 重要：设备ID是全局唯一的

**数据库层面**：
- `DeviceInstanceEntity` 的 `id` 字段是**主键**（PRIMARY KEY）
- 主键在数据库中**必须唯一**，不能重复
- 如果尝试创建相同ID的设备，数据库会报错（主键冲突）

**代码证据**：
```java
// DeviceInstanceEntity.java
public class DeviceInstanceEntity extends GenericEntity<String> {
    @Override
    @GeneratedValue(generator = Generators.SNOW_FLAKE)
    public String getId() {
        return super.getId();  // ← 主键，全局唯一
    }
    
    @Column(name = "product_id", updatable = false)
    private String productId;  // ← 产品ID，可以不同
}
```

### 结论

**如果产品不一样，设备ID不一样，设备ID也不可能一样**（数据库主键约束）

如果同一个物理设备需要使用不同协议：
- ✅ **必须使用不同的设备ID**
- ✅ 创建多个设备实例，每个实例使用不同的设备ID
- ✅ 虽然代表同一个物理设备，但在 JetLinks 中是独立的设备实例

## 总结

| 问题 | 答案 |
|------|------|
| 一个设备可以使用多个消息协议吗？ | ❌ 不可以，一个设备只能使用一个消息协议（通过产品配置） |
| 一个协议可以支持多种传输协议吗？ | ✅ 可以，如 `jetlinks.v3.2` 支持 TCP、MQTT、HTTP 等 |
| 设备ID可以重复吗？ | ❌ **不可以，设备ID是全局唯一的主键** |
| 产品不同，设备ID可以相同吗？ | ❌ **不可以，数据库主键冲突** |
| 如何让一个物理设备使用不同协议？ | 创建多个产品，每个产品配置不同协议，然后创建多个设备实例（使用不同的设备ID） |
| 如何让一个设备使用同一协议的不同传输方式？ | 创建多个产品（相同消息协议，不同传输协议），然后创建多个设备实例（使用不同的设备ID） |

## 推荐做法

### ✅ 推荐：保持协议独立

- **职责清晰**：每个协议服务于特定业务场景
- **易于管理**：产品 → 设备 → 协议的层级关系清晰
- **配置简单**：用户根据设备类型选择对应产品

### 如果需要多协议支持

1. **创建多个产品**：每个产品对应一个协议
2. **创建多个设备实例**：每个设备实例关联到不同的产品
3. **通过不同网关接入**：每个网关使用对应的协议

