package org.jetlinks.protocol.official.parallel.tcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.config.ConfigKey;
import org.jetlinks.core.defaults.BlockingDeviceOperator;
import org.jetlinks.core.message.*;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.core.metadata.DefaultConfigMetadata;
import org.jetlinks.core.metadata.types.BooleanType;
import org.jetlinks.core.metadata.types.StringType;
import org.jetlinks.core.monitor.logger.Logger;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.protocol.official.ObjectMappers;
import org.jetlinks.protocol.official.parallel.ParallelDeviceSession;
import org.jetlinks.protocol.official.parallel.cipher.ParallelCipher;
import org.jetlinks.protocol.official.parallel.message.*;
import org.jetlinks.supports.protocol.blocking.BlockingDeviceMessageCodec;
import org.jetlinks.supports.protocol.blocking.BlockingMessageDecodeContext;
import org.jetlinks.supports.protocol.blocking.BlockingMessageEncodeContext;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平行驾驶 TCP 设备消息编解码器
 * 实现 RSA+AES 密钥认证流程和加密通信
 */
@Slf4j
public class ParallelTcpDeviceMessageCodec extends BlockingDeviceMessageCodec {

    public static final ConfigKey<String> CONFIG_KEY_VIN = ConfigKey.of("vin");
    public static final ConfigKey<String> CONFIG_KEY_OMS_NO = ConfigKey.of("omsno");
    public static final ConfigKey<Boolean> CONFIG_KEY_ENABLE_ENCRYPT = ConfigKey.of("enableEncrypt");

    public static final DefaultConfigMetadata tcpConfig = new DefaultConfigMetadata(
        "平行驾驶TCP认证配置", "")
        .add(CONFIG_KEY_VIN.getKey(), "VIN码", "车辆VIN码或手柄端编号（如：MVXRCM1F2XLA00001 或 JOY-G29-00001）", new StringType())
        .add(CONFIG_KEY_OMS_NO.getKey(), "OMS编号", "平行驾驶设备OMS编号", new StringType())
        .add(CONFIG_KEY_ENABLE_ENCRYPT.getKey(), "启用加密", "是否启用通信加密（默认关闭）", new BooleanType());

    private final ObjectMapper objectMapper = ObjectMappers.JSON_MAPPER;
    
    // 存储设备会话（key: deviceId）
    private final Map<String, ParallelDeviceSession> sessions = new ConcurrentHashMap<>();

    public ParallelTcpDeviceMessageCodec(ServiceContext context) {
        super(context, DefaultTransport.TCP);
    }

    private static final byte JSON_OBJECT_FIRST_BYTE = '{'; // 0x7B

    @Override
    protected void upstream(BlockingMessageDecodeContext context) {
        ByteBuf payload = context.getData().getPayload();
        Logger logger = context.logger();

        if (logger.isDebugEnabled()) {
            logger.debug("收到设备TCP报文: {}", ByteBufUtil.hexDump(payload));
        }

        if (payload.readableBytes() == 0) {
            logger.warn("消息为空");
            return;
        }

        final ByteBuf messageBuf;
        // 兼容两种 TCP 行为：① 仅 body（TCP 已按长度拆包）；② 4 字节大端长度 + body
        if (payload.getByte(payload.readerIndex()) == JSON_OBJECT_FIRST_BYTE) {
            // 首字节为 '{'，视为纯 JSON body，不再读长度头
            messageBuf = payload.readBytes(payload.readableBytes());
        } else {
            if (payload.readableBytes() < 4) {
                logger.warn("消息长度不足4字节");
                return;
            }
            int messageLength = payload.readInt();
            if (messageLength < 0 || messageLength > 1024 * 1024) {
                logger.warn("非法消息长度: {}，忽略本包", messageLength);
                return;
            }
            if (payload.readableBytes() < messageLength) {
                logger.warn("消息长度不完整，期望: {}, 实际: {}", messageLength, payload.readableBytes());
                return;
            }
            messageBuf = payload.readBytes(messageLength);
        }

        String messageJson;
        
        BlockingDeviceOperator device = context.getDevice();
        ParallelDeviceSession session = device != null ? sessions.get(device.getDeviceId()) : null;

        // 如果已认证且启用加密，需要解密
        boolean enableEncrypt = false;
        if (device != null) {
            Boolean configValue = device.getConfigNow(CONFIG_KEY_ENABLE_ENCRYPT);
            enableEncrypt = configValue != null && configValue;
        }
        
        if (session != null && session.isAuthenticated() && enableEncrypt) {
            try {
                String encryptedJson = messageBuf.toString(StandardCharsets.UTF_8);
                messageJson = ParallelCipher.aesDecrypt(encryptedJson, session.getAesKey().get());
            } catch (Exception e) {
                logger.error("AES解密失败", e);
                messageBuf.release();
                return;
            }
        } else {
            messageJson = messageBuf.toString(StandardCharsets.UTF_8);
        }

        messageBuf.release();

        // 仅当内容像 JSON（以 { 或 [ 开头）时才解析，避免把二进制/加密体或长度头当 JSON 导致 CTRL-CHAR 报错
        String trimmed = messageJson != null ? messageJson.trim() : "";
        if (trimmed.isEmpty() || (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[')) {
            int firstCode = trimmed.isEmpty() ? -1 : (int) trimmed.charAt(0);
            logger.warn("非 JSON 报文，已忽略（首字符 code={}，可能为长度头或加密体）", firstCode);
            return;
        }

        try {
            // 解析 JSON 消息
            Map<String, Object> messageMap = objectMapper.readValue(messageJson, Map.class);
            String messageName = (String) messageMap.get("name");

            if (device == null) {
                // 未认证设备，处理认证流程
                handleAuthFlow(messageName, messageMap, context, logger);
            } else {
                // 已认证设备，处理业务消息
                handleBusinessMessage(messageName, messageMap, device, session, context, logger);
            }
        } catch (Exception e) {
            logger.error("解析消息失败", e);
        }
    }

    /**
     * 处理认证流程
     */
    private void handleAuthFlow(String messageName, Map<String, Object> messageMap,
                                BlockingMessageDecodeContext context, Logger logger) {
        if (!"authreq".equals(messageName)) {
            logger.warn("设备未认证，但收到非认证请求消息: {}", messageName);
            return;
        }

        try {
            AuthRequestMessage authReq = objectMapper.convertValue(messageMap, AuthRequestMessage.class);
            String vin = authReq.getVin();
            String omsno = authReq.getOmsno();
            String deviceId = authReq.getId();

            // 根据 VIN 和 OMS 编号查找设备
            // 注意：这里需要根据实际业务逻辑查找设备
            // 可能需要通过 VIN 或 OMS 编号查找，而不是 deviceId
            BlockingDeviceOperator device = context.getDevice(deviceId);
            if (device == null) {
                // 尝试通过 VIN 查找设备（如果支持）
                logger.warn("设备不存在: deviceId={}, vin={}, omsno={}", deviceId, vin, omsno);
                sendAuthOver(deviceId, vin, omsno, authReq.getSeq(), "设备不存在", context, false);
                return;
            }

            // 验证 VIN 和 OMS 编号
            String configVin = device.getConfigNow(CONFIG_KEY_VIN);
            String configOmsNo = device.getConfigNow(CONFIG_KEY_OMS_NO);

            if (!vin.equals(configVin) || !omsno.equals(configOmsNo)) {
                logger.warn("VIN或OMS编号不匹配: deviceId={}, vin={}, omsno={}", deviceId, vin, omsno);
                sendAuthOver(deviceId, vin, omsno, authReq.getSeq(), "VIN或OMS编号不匹配", context, false);
                return;
            }

            // 检查是否启用加密
            boolean enableEncrypt = Boolean.TRUE.equals(device.getConfigNow(CONFIG_KEY_ENABLE_ENCRYPT));
            
            if (enableEncrypt) {
                // 创建会话并开始认证
                ParallelDeviceSession session = new ParallelDeviceSession(deviceId, vin, omsno);
                session.startAuth();
                sessions.put(deviceId, session);

                // 发送 RSA 公钥
                sendRsaPublicKey(deviceId, vin, omsno, authReq.getSeq(), session, context, logger);
            } else {
                // 不启用加密，直接上线
                DeviceOnlineMessage onlineMessage = new DeviceOnlineMessage();
                onlineMessage.setDeviceId(deviceId);
                onlineMessage.setTimestamp(System.currentTimeMillis());
                context.sendToPlatformLater(onlineMessage);
                
                // 发送认证完成消息（不加密）
                sendAuthOver(deviceId, vin, omsno, authReq.getSeq(), "ok", context, false);
            }

        } catch (Exception e) {
            logger.error("处理认证请求失败", e);
        }
    }

    /**
     * 发送 RSA 公钥
     */
    private void sendRsaPublicKey(String deviceId, String vin, String omsno, String seq,
                                   ParallelDeviceSession session, BlockingMessageDecodeContext context,
                                   Logger logger) {
        try {
            RsaPublicKeyMessage rsaPub = new RsaPublicKeyMessage();
            rsaPub.setVin(vin);
            rsaPub.setId(deviceId);
            rsaPub.setType("oms");
            rsaPub.setOmsno(omsno);
            rsaPub.setSeq(seq);
            rsaPub.setVersion("1.0");
            rsaPub.setTimestamp(java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            rsaPub.setRsapubKey(session.getRsaPublicKeyBase64());

            session.setAuthState(ParallelDeviceSession.AuthState.WAITING_AES_KEY);

            String json = objectMapper.writeValueAsString(rsaPub);
            sendMessage(json, context);

            logger.info("已发送RSA公钥: deviceId={}, vin={}", deviceId, vin);

        } catch (Exception e) {
            logger.error("发送RSA公钥失败", e);
        }
    }

    /**
     * 处理 AES 密钥消息
     */
    private void handleAesKeyMessage(AesKeyMessage aesKeyMsg, BlockingMessageDecodeContext context,
                                      Logger logger) {
        String deviceId = aesKeyMsg.getId();
        ParallelDeviceSession session = sessions.get(deviceId);

        if (session == null || session.getAuthState() != ParallelDeviceSession.AuthState.WAITING_AES_KEY) {
            logger.warn("收到AES密钥消息，但会话状态不正确: deviceId={}", deviceId);
            sendAuthOver(deviceId, aesKeyMsg.getVin(), aesKeyMsg.getOmsno(), aesKeyMsg.getSeq(),
                        "认证流程错误", context, true);
            return;
        }

        if (session.isAuthTimeout()) {
            logger.warn("认证超时: deviceId={}", deviceId);
            sessions.remove(deviceId);
            sendAuthOver(deviceId, aesKeyMsg.getVin(), aesKeyMsg.getOmsno(), aesKeyMsg.getSeq(),
                        "认证超时", context, true);
            return;
        }

        try {
            // 使用 RSA 私钥解密 AES 密钥
            String encryptedAesKey = aesKeyMsg.getAesKey();
            String aesKey = ParallelCipher.rsaDecrypt(encryptedAesKey, session.getRsaPrivateKeyBase64());

            // 保存 AES 密钥
            session.getAesKey().set(aesKey);
            session.setAuthState(ParallelDeviceSession.AuthState.AUTHENTICATED);

            // 发送认证完成消息（加密模式）
            sendAuthOver(deviceId, aesKeyMsg.getVin(), aesKeyMsg.getOmsno(), aesKeyMsg.getSeq(),
                        "ok", context, true);

            // 发送设备上线消息到平台
            DeviceOnlineMessage onlineMessage = new DeviceOnlineMessage();
            onlineMessage.setDeviceId(deviceId);
            onlineMessage.setTimestamp(System.currentTimeMillis());
            context.sendToPlatformLater(onlineMessage);

            logger.info("认证完成: deviceId={}, vin={}", deviceId, aesKeyMsg.getVin());

        } catch (Exception e) {
            logger.error("处理AES密钥失败", e);
            sendAuthOver(deviceId, aesKeyMsg.getVin(), aesKeyMsg.getOmsno(), aesKeyMsg.getSeq(),
                        "解密AES密钥失败", context, true);
        }
    }

    /**
     * 发送认证完成消息
     */
    private void sendAuthOver(String deviceId, String vin, String omsno, String seq,
                              String result, BlockingMessageDecodeContext context, boolean encrypted) {
        try {
            AuthOverMessage authOver = new AuthOverMessage();
            authOver.setVin(vin);
            authOver.setId(deviceId);
            authOver.setType("oms");
            authOver.setOmsno(omsno);
            authOver.setSeq(seq);
            authOver.setVersion("1.0");
            authOver.setTimestamp(java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            authOver.setAuthResult(result);

            String json = objectMapper.writeValueAsString(authOver);
            
            // 如果启用加密且已认证，需要加密发送
            if (encrypted) {
                ParallelDeviceSession session = sessions.get(deviceId);
                if (session != null && session.isAuthenticated()) {
                    String encryptedJson = ParallelCipher.aesEncrypt(json, session.getAesKey().get());
                    sendMessage(encryptedJson, context);
                } else {
                    sendMessage(json, context);
                }
            } else {
                sendMessage(json, context);
            }

        } catch (Exception e) {
            log.error("发送认证完成消息失败", e);
        }
    }

    /**
     * 处理业务消息
     */
    private void handleBusinessMessage(String messageName, Map<String, Object> messageMap,
                                       BlockingDeviceOperator device, ParallelDeviceSession session,
                                       BlockingMessageDecodeContext context, Logger logger) {
        if (session == null || !session.isAuthenticated()) {
            logger.warn("设备未完成认证: deviceId={}", device.getDeviceId());
            return;
        }

        // 处理 aespub 消息（AES 密钥）
        if ("aespub".equals(messageName)) {
            try {
                AesKeyMessage aesKeyMsg = objectMapper.convertValue(messageMap, AesKeyMessage.class);
                handleAesKeyMessage(aesKeyMsg, context, logger);
            } catch (Exception e) {
                logger.error("处理AES密钥消息失败", e);
            }
            return;
        }

        // 根据消息类型处理业务消息
        // 这里可以根据实际需求扩展
        logger.debug("处理业务消息: name={}, deviceId={}", messageName, device.getDeviceId());
    }

    @Override
    protected void downstream(BlockingMessageEncodeContext context) {
        DeviceMessage message = context.getMessage();
        BlockingDeviceOperator device = context.getDevice();
        
        if (device == null) {
            return;
        }

        // 检查是否启用加密
        boolean enableEncrypt = Boolean.TRUE.equals(device.getConfigNow(CONFIG_KEY_ENABLE_ENCRYPT));
        
        if (enableEncrypt) {
            ParallelDeviceSession session = sessions.get(device.getDeviceId());
            if (session == null || !session.isAuthenticated()) {
                log.warn("设备未认证，无法发送下行消息: deviceId={}", device.getDeviceId());
                return;
            }

            try {
                // 将消息转换为 JSON
                String json = objectMapper.writeValueAsString(message);
                
                // AES 加密
                String encryptedJson = ParallelCipher.aesEncrypt(json, session.getAesKey().get());
                
                // 发送加密后的消息
                ByteBuf buf = Unpooled.buffer();
                buf.writeInt(encryptedJson.getBytes(StandardCharsets.UTF_8).length);
                buf.writeBytes(encryptedJson.getBytes(StandardCharsets.UTF_8));
                
                context.sendToDeviceLater(EncodedMessage.simple(buf));

            } catch (Exception e) {
                log.error("编码下行消息失败", e);
            }
        } else {
            // 不加密，直接发送
            try {
                String json = objectMapper.writeValueAsString(message);
                ByteBuf buf = Unpooled.buffer();
                buf.writeInt(json.getBytes(StandardCharsets.UTF_8).length);
                buf.writeBytes(json.getBytes(StandardCharsets.UTF_8));
                
                context.sendToDeviceLater(EncodedMessage.simple(buf));
            } catch (Exception e) {
                log.error("编码下行消息失败", e);
            }
        }
    }

    /**
     * 发送消息到设备
     */
    private void sendMessage(String json, BlockingMessageDecodeContext context) {
        try {
            ByteBuf buf = Unpooled.buffer();
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(jsonBytes.length);
            buf.writeBytes(jsonBytes);
            
            context.sendToDeviceLater(EncodedMessage.simple(buf));
        } catch (Exception e) {
            log.error("发送消息失败", e);
        }
    }
}

