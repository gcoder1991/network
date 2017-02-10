package org.gcoder.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.gcoder.network.kcp.KcpLoopGroup;
import org.gcoder.network.kcp.KcpSession;
import org.gcoder.network.netty.UdpServer;
import org.gcoder.session.DefaultSessionManager;

import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Created by Administrator on 2017/2/10.
 */
public class KcpServer extends UdpServer {

    public KcpServer(int port) {
        super(port);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ch.pipeline().addLast(new ServerHandler());
    }

    class ServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        private final DefaultSessionManager sessionManager = new DefaultSessionManager(new KcpLoopGroup(1),new ScheduledThreadPoolExecutor(1));

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            KcpSession session = sessionManager.get(1);
            if (session == null){
                session = sessionManager.create(1, msg.sender(), ctx.channel());
            }
            session.inputReliable(msg.content());
        }
    }

    public static void main(String[] args) {
        KcpServer server = new KcpServer(40000);
        try {
            server.bind().sync().channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
