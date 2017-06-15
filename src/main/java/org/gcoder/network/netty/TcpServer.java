package org.gcoder.network.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.gcoder.network.protocol.TransmissionProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * Created by gcoder on 2017/1/24.
 */
public abstract class TcpServer extends ChannelInitializer implements NettyServer {

    private static final Logger LOG = LoggerFactory.getLogger(TcpServer.class);

    private final ServerBootstrap bootstrap;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workGroup;

    private final InetSocketAddress localAddress;

    private Channel channel;

    public TcpServer(int port) {
        this(new InetSocketAddress(port), NioServerSocketChannel.class);
    }

    public TcpServer(InetSocketAddress remoteAddress, Class<? extends ServerChannel> socketClass) {
        this(new NioEventLoopGroup(), new NioEventLoopGroup(), remoteAddress, socketClass);
    }

    public TcpServer(EventLoopGroup bossGroup, EventLoopGroup workGroup, InetSocketAddress localAddress) {
        this(bossGroup, workGroup, localAddress, NioServerSocketChannel.class);
    }

    public TcpServer(EventLoopGroup bossGroup, EventLoopGroup workGroup, InetSocketAddress localAddress, Class<? extends ServerChannel> socketClass) {
        this.bossGroup = bossGroup;
        this.workGroup = workGroup;
        this.localAddress = localAddress;

        this.bootstrap = new ServerBootstrap()
                .group(bossGroup)
                .channel(socketClass)
                .localAddress(localAddress)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(this);
    }

    @Override
    public ChannelFuture bind() throws Exception {
        ChannelFuture channelFuture = bootstrap.bind();
        this.channel = channelFuture.channel();
        if (LOG.isDebugEnabled()) {
            LOG.debug("TCP Server bind to {}", localAddress);
        }
        return channelFuture;
    }

    @Override
    public ChannelFuture stop() throws Exception {
        if (channel != null) {
            return channel.close();
        }
        return null;
    }

    @Override
    public TransmissionProtocol geTransmissionProtocol() {
        return TransmissionProtocol.TCP;
    }
}
