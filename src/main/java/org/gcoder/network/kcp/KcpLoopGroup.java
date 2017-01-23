package org.gcoder.network.kcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KcpLoopGroup extends ThreadGroup {

    public final static String NAME = "KCP_LOOP_GROUP";

    private List<KcpLoopThread> threads = new ArrayList<>();

    public KcpLoopGroup() {
        this(1);
    }

    public KcpLoopGroup(int num) {
        super(NAME);
        if (num <= 0) {
            num = 1;
        }
        for (int i = 0; i < num; i++) {
            KcpLoopThread thread = new KcpLoopThread(this, i);
            thread.start();
            threads.add(thread);
        }
    }

    public synchronized KcpLoopThread register(KcpSession session) {
        if (threads.size() > 0) {
            Collections.sort(threads);
            return threads.get(0).register(session);
        } else {
            throw new UnsupportedOperationException("NO KCP LOOP THREAD");
        }
    }

    public synchronized void deregister(KcpSession session) {
        KcpLoopThread loopThread = session.getLoopThread();
        if (loopThread != null) {
            loopThread.deregister(session);
        }
    }


}
