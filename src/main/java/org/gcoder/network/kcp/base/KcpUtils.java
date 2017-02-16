package org.gcoder.network.kcp.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;

final class KcpUtils {

    private final static Logger LOG = LoggerFactory.getLogger(KcpUtils.class);

    private static int bound(int lower, int middle, int upper) {
        return Math.min(Math.max(lower, middle), upper);
    }

    static int timeDiff(int later, int earlier) {
        return later - earlier;
    }

    /**
     * 用户数据包解析
     *
     * @param kcp
     * @param newSeg
     */
    static void parseData(Kcp kcp, Segment newSeg) {
        int sn = newSeg.sn;
        boolean repeat = false;
        if (timeDiff(sn, kcp.rcv_nxt + kcp.rcv_wnd) >= 0 || timeDiff(sn, kcp.rcv_nxt) < 0) {
            newSeg.release();
            return;
        }

        // 判断是否是重复包，并且计算插入位置
        int after = -1;
        for (int i = kcp.rcv_buf.size() - 1; i >= 0; i--) {
            Segment seg = kcp.rcv_buf.get(i);
            if (seg.sn == sn) {
                repeat = true;
                break;
            }
            if (timeDiff(sn, seg.sn) > 0) {
                after = i;
                break;
            }
        }

        if (!repeat) {
            // 如果不是重复包，则插入
            if (after == -1) {
                kcp.rcv_buf.add(0, newSeg);
            } else {
                kcp.rcv_buf.add(after + 1, newSeg);
            }
        } else {
            newSeg.release();
        }

        LOG.trace("rcv_nxt={}", kcp.rcv_nxt);

        // move available data from rcv_buf -> rcv_queue
        // 将连续包加入到接收队列
        List<Segment> rcv_buf = kcp.rcv_buf;
        while (!rcv_buf.isEmpty()) {
            Segment seg = rcv_buf.get(0);
            if (seg.sn == kcp.rcv_nxt && kcp.rcv_queue.size() < kcp.rcv_wnd) {
                kcp.rcv_queue.add(rcv_buf.remove(0));
                kcp.rcv_nxt++;
            } else {
                break;
            }
        }

        LOG.trace("rcv_next={}", kcp.rcv_nxt);

        LOG.trace("snd(buf={}, queue={})", kcp.snd_buf.size(), kcp.snd_queue.size());
        LOG.trace("rcv(buf={}, queue={})", kcp.rcv_buf.size(), kcp.rcv_queue.size());
    }

    static void parseUna(Kcp kcp, int una) {
        List<Segment> snd_buf = kcp.snd_buf;
        while (!snd_buf.isEmpty()) {
            Segment peek = snd_buf.get(0);
            if (timeDiff(una, peek.sn) > 0) {
                snd_buf.remove(0).release();
            } else {
                break;
            }
        }
    }

    /**
     * 计算本地真实snd_una
     *
     * @param kcp
     */
    static void shrinkBuf(Kcp kcp) {
        if (kcp.snd_buf.size() > 0) {
            kcp.snd_una = kcp.snd_buf.get(0).sn;
        } else {
            kcp.snd_una = kcp.snd_nxt;
        }
    }

    /**
     * 对端返回的ack, 确认发送成功时，对应包从发送缓存中移除
     *
     * @param kcp
     * @param sn
     */
    static void parseAck(Kcp kcp, int sn) {
        if (timeDiff(sn, kcp.snd_una) < 0 || timeDiff(sn, kcp.snd_nxt) >= 0) {
            return;
        }
        List<Segment> snd_buf = kcp.snd_buf;
        Iterator<Segment> iterator = snd_buf.iterator();
        while (iterator.hasNext()) {
            Segment peek = iterator.next();
            if (sn == peek.sn) {
                iterator.remove();
                peek.release();
                break;
            }
            if (timeDiff(sn, peek.sn) < 0) {
                break;
            }
        }
    }

    /**
     * ack append
     *
     * @param kcp
     * @param sn
     * @param ts
     */
    static void pushAck(Kcp kcp, int sn, int ts) {
        kcp.ackList.add(sn);
        kcp.ackList.add(ts);
    }

    static void updateAck(Kcp kcp, int rtt) {
        if (kcp.rx_srtt == 0) {
            kcp.rx_srtt = rtt;
            kcp.rx_rttval = rtt / 2;
        } else {
            int delta = rtt - kcp.rx_srtt;
            if (delta < 0) {
                delta = -delta;
            }
            kcp.rx_rttval = (3 * kcp.rx_rttval + delta) / 4;
            kcp.rx_srtt = (7 * kcp.rx_srtt + rtt) / 8;
            if (kcp.rx_srtt < 1) {
                kcp.rx_srtt = 1;
            }
        }
        int rto = kcp.rx_srtt + Math.max(1, 4 * kcp.rx_rttval);
        kcp.rx_rto = bound(kcp.rx_minrto, rto, KcpBasic.KCP_RTO_MAX);
    }

    static void parseFastAck(Kcp kcp, int sn) {
        if (timeDiff(sn, kcp.snd_una) < 0 || timeDiff(sn, kcp.snd_nxt) >= 0) {
            return;
        }
        for (Segment seg : kcp.snd_buf) {
            if (timeDiff(sn, seg.sn) < 0) {
                break;
            } else if (sn != seg.sn) {
                seg.fastack++;
            }
        }
    }

    static int wndUnused(Kcp kcp) {
        if (kcp.rcv_queue.size() < kcp.rcv_wnd) {
            return kcp.rcv_wnd - kcp.rcv_queue.size();
        }
        return 0;
    }

}
