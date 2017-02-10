package org.gcoder.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import org.gcoder.network.kcp.KcpLoopGroup;
import org.gcoder.network.kcp.KcpSession;
import org.gcoder.network.netty.UdpClient;
import org.gcoder.session.DefaultSessionManager;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Created by gcoder on 2017/2/10.
 */
public class KcpClient extends UdpClient {

    private final InetSocketAddress server;

    public KcpClient(InetSocketAddress server) {
        this.server = server;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ch.pipeline().addLast(new DefaultHandler(server));
    }

    class DefaultHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        private final CountDownLatch semaphore = new CountDownLatch(1);

        private final InetSocketAddress server;

        final DefaultSessionManager sessionManager = new DefaultSessionManager(new KcpLoopGroup(1),new ScheduledThreadPoolExecutor(1));

        public DefaultHandler(InetSocketAddress server) {
            this.server = server;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            sessionManager.create(1, server, ctx.channel());
            semaphore.countDown();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            sessionManager.get(1).inputReliable(msg.content());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.out.println(cause);
        }

        protected void sayHi() throws InterruptedException {
            semaphore.await();
            KcpSession session = sessionManager.get(1);
            ByteBuf data = session.getAllocator().buffer().writeBytes("Hi".getBytes());
            session.outputReliable(data);
        }

    }

    public static void main(String[] args) {
        try {
            ChannelFuture future = new KcpClient(new InetSocketAddress("127.0.0.1", 40000)).connect().sync();
            future.addListener((ChannelFutureListener) future1 -> {
                if(future1.isSuccess()){
                    DefaultHandler handler = future1.channel().pipeline().get(DefaultHandler.class);
                    handler.sayHi();
                }else{
                    System.out.println("error!!");
                }

            });
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
