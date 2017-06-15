package org.gcoder.network.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.gcoder.network.protocol.TransmissionProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * Created by gcoder on 2017/1/24.
 */
public abstract class UdpServer extends ChannelInitializer implements NettyServer {

    private static final Logger LOG = LoggerFactory.getLogger(UdpServer.class);

    private final EventLoopGroup group;
    private final InetSocketAddress localAddress;
    private final Bootstrap bootstrap;

    private Channel channel;

    public UdpServer(int port) {
        this(new NioEventLoopGroup(), new InetSocketAddress(port));
    }

    public UdpServer(EventLoopGroup group, InetSocketAddress localAddress) {
        this.group = group;
        this.localAddress = localAddress;
        this.bootstrap = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .localAddress(localAddress)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(this);
    }

    @Override
    public ChannelFuture bind() throws Exception {
        ChannelFuture future = bootstrap.bind();
        this.channel = future.channel();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Udp Server bind to {}", localAddress);
        }
        return future;
    }

    @Override
    public ChannelFuture stop() throws Exception {
        if (channel != null) {
            channel.close();
        }
        return null;
    }

    @Override
    public TransmissionProtocol geTransmissionProtocol() {
        return TransmissionProtocol.UDP;
    }
}
