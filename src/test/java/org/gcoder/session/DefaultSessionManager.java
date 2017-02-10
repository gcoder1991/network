package org.gcoder.session;

import io.netty.channel.Channel;
import org.gcoder.network.kcp.KcpLoopGroup;
import org.gcoder.network.kcp.KcpSession;
import org.gcoder.network.kcp.KcpSessionManager;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by gcoder on 2017/2/10.
 */
public class DefaultSessionManager implements KcpSessionManager<Integer>,Runnable {

    private final KcpLoopGroup group;
    private final ScheduledExecutorService scheduled;

    private ConcurrentHashMap<Integer, KcpSession> sessionGroup = new ConcurrentHashMap<>();

    public DefaultSessionManager(KcpLoopGroup group, ScheduledExecutorService scheduled) {
        this.group = group;
        this.scheduled = scheduled;
    }

    @Override
    public void run() {

    }

    @Override
    public KcpSession create(Integer conv, InetSocketAddress addr, Channel channel) {
        return sessionGroup.put(conv, new DefaultSession(new DefaultKcp(conv, addr, channel), this));
    }

    @Override
    public KcpSession remove(Integer conv) {
        return sessionGroup.remove(conv);
    }

    @Override
    public KcpSession get(Integer key) {
        return sessionGroup.get(key);
    }

    @Override
    public KcpLoopGroup getKcpLoopGroup() {
        return group;
    }

    @Override
    public Executor getExecutor() {
        return scheduled;
    }
}
