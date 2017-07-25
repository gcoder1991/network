package org.gcoder.network.kcp;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

/**
 * Created by gcoder on 2017/2/10.
 */
public interface KcpSessionManager<K,T> {

   // KcpSession create(K key, InetSocketAddress addr, Channel channel, T target);
    KcpSession create(K key, InetSocketAddress addr, Channel channel);
    
    KcpSession remove(K key);

    KcpSession get(K key);

    KcpLoopGroup getKcpLoopGroup();

    Executor getExecutor();

}
