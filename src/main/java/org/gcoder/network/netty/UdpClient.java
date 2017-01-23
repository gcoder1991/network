package org.gcoder.network.netty;


import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.gcoder.network.protocol.TransmissionProtocol;

public abstract class UdpClient extends ChannelInitializer<Channel> implements NettyClient {

    private EventLoopGroup bossGroup;
    private Bootstrap bootstrap;

    private Channel channel;

    public UdpClient() {
        this(new NioEventLoopGroup());
    }

    public UdpClient(EventLoopGroup bossGroup) {
        this.bossGroup = bossGroup;
        this.bootstrap = new Bootstrap()
                .group(bossGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(this);
    }

    public ChannelFuture connect() throws Exception {
        ChannelFuture channelFuture = bootstrap.bind(0);
        channel = channelFuture.channel();
        return channelFuture;
    }

    public ChannelFuture stop() throws Exception {
        return channel.close();
    }

    public TransmissionProtocol geTransmissionProtocol() {
        return TransmissionProtocol.UDP;
    }

    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

}
