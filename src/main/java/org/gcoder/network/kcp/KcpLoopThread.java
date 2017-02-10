package org.gcoder.network.kcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class KcpLoopThread extends Thread implements Comparable<KcpLoopThread> {

    private static final Logger LOG = LoggerFactory.getLogger(KcpLoopThread.class);

    private Map<Integer, KcpSession> sessionGroup = new ConcurrentHashMap<>();

    private AtomicInteger holdNum = new AtomicInteger(0);

    public KcpLoopThread(ThreadGroup group, int index) {
        super(group, group.getName().concat("_Thread-").concat(String.valueOf(index)));
        LOG.debug("KCP THREAD INIT : {}", this.getName());
    }

    public KcpLoopThread register(KcpSession session) {
        sessionGroup.put(session.getConv(), session);
        int num = holdNum.incrementAndGet();
        LOG.debug("{} register kcp session {} : num = {}", new Object[]{this.getName(), session.getConv(), num});
        return this;
    }

    public void deregister(KcpSession session) {
        sessionGroup.remove(session.getConv());
        holdNum.decrementAndGet();
    }

    @Override
    public void run() {
        while (!this.isInterrupted()) {

            sessionGroup.values().parallelStream().forEach((session) -> {
                try {
                    session.onTick();
                } catch (Exception e) {
                    LOG.error(getName(), e);
                }
            });

            try {
                sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        sessionGroup.values().parallelStream().forEach((session) -> session.close());
        sessionGroup.clear();
    }

    @Override
    public int compareTo(KcpLoopThread o) {
        return Integer.compare(this.holdNum.get(), o.getHoldKcp().get());
    }

    public AtomicInteger getHoldKcp() {
        return holdNum;
    }

}
