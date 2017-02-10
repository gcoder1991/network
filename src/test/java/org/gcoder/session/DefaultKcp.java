package org.gcoder.session;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import org.gcoder.network.kcp.base.Kcp;

import java.net.InetSocketAddress;

/**
 * Created by Administrator on 2017/2/10.
 */
public class DefaultKcp extends Kcp {

    private final Channel channel;

    /**
     * 和 tcp 的conv一样，通信双发需要保证conv相同，相互的数据包才能被认可 c->ikcp_create
     *
     * @param conv
     * @param user
     * @param channel
     */
    public DefaultKcp(int conv, InetSocketAddress user, Channel channel) {
        super(conv, user, channel.alloc());
        this.channel = channel;
        this.setNodelay(true,10,2,false);
        this.setMtu(1300);
        this.setWndSize(128,128);
    }

    @Override
    public void output(ByteBuf data) {
        channel.writeAndFlush(new DatagramPacket(data,user));
    }
}
