package org.jetlinks.protocol.official.parallel;

import org.jetlinks.core.defaults.CompositeProtocolSupport;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.spi.ProtocolSupportProvider;
import org.jetlinks.core.spi.ServiceContext;
import org.jetlinks.protocol.official.parallel.tcp.ParallelTcpDeviceMessageCodec;
import org.jetlinks.supports.official.JetLinksDeviceMetadataCodec;
import reactor.core.publisher.Mono;

/**
 * 平行驾驶协议支持提供者
 */
public class ParallelProtocolSupportProvider implements ProtocolSupportProvider {

    @Override
    public Mono<CompositeProtocolSupport> create(ServiceContext context) {
        return Mono.defer(() -> {
            CompositeProtocolSupport support = new CompositeProtocolSupport();

            support.setId("parallel-driving");
            support.setName("平行驾驶协议");
            support.setDescription("平行驾驶设备接入协议，支持RSA+AES密钥认证");

            // TCP 支持
            support.addConfigMetadata(DefaultTransport.TCP, ParallelTcpDeviceMessageCodec.tcpConfig);
            support.addMessageCodecSupport(new ParallelTcpDeviceMessageCodec(context));

            // 元数据编解码器
            support.setMetadataCodec(new JetLinksDeviceMetadataCodec());

            return Mono.just(support);
        });
    }
}

