package org.gcoder.network.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.gcoder.network.protocol.TransmissionProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public abstract class TcpClient extends ChannelInitializer<SocketChannel> implements NettyClient {

    private final static Logger LOG = LoggerFactory.getLogger(TcpClient.class);

    private final EventLoopGroup bossGroup;
    private final Bootstrap bootstrap;

    private Channel channel;
    private InetSocketAddress remoteAddress;

    public TcpClient(InetSocketAddress remoteAddress) {
        this(new NioEventLoopGroup(), remoteAddress);
    }

    public TcpClient(EventLoopGroup bossGroup, InetSocketAddress remoteAddress) {
        this(bossGroup, remoteAddress, NioSocketChannel.class);
    }

    public TcpClient(EventLoopGroup bossGroup, InetSocketAddress remoteAddress, Class<? extends SocketChannel> socketClass) {
        this.bossGroup = bossGroup;
        this.bootstrap = new Bootstrap()
                .group(bossGroup)
                .channel(socketClass)
                .remoteAddress(remoteAddress)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(this);
        this.remoteAddress = remoteAddress;
    }


    public ChannelFuture connect() throws Exception {
        ChannelFuture channelFuture = bootstrap.connect();
        channel = channelFuture.channel();
        LOG.debug("TCP Client connect to {}", remoteAddress);
        return channelFuture;
    }


    public ChannelFuture stop() {
        return channel.close();
    }


    public TransmissionProtocol geTransmissionProtocol() {
        return TransmissionProtocol.TCP;
    }

    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }


}
