package org.gcoder.network.kcp.base;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ReferenceCountUtil;

/**
 * KCP - A Better ARQ Protocol Implementation
 * <p>
 * Features: + Average RTT reduce 30% - 40% vs traditional ARQ like tcp. +
 * Maximum RTT reduce three times vs tcp. + Lightweight, distributed as a single
 * source file.
 */
public abstract class Kcp {

    private final static Logger LOG = LoggerFactory.getLogger(Kcp.class);

    protected final ByteBufAllocator allocator;

    protected final int conv; // 会话编号

    protected int mtu = KcpBasic.KCP_MTU_DEF; // 最大传输单元
    protected int mss = KcpBasic.KCP_MTU_DEF - KcpBasic.KCP_OVERHEAD; // 最大分段大小
    protected int state = 0;

    protected int snd_una = 0;
    protected int snd_nxt = 0;
    protected int rcv_nxt = 0;

    protected int ts_recent = 0;
    protected int ts_lastack = 0;
    protected int ssthresh = KcpBasic.KCP_THRESH_INIT;

    protected int rx_rttval = 0;
    protected int rx_srtt = 0;
    protected int rx_rto = KcpBasic.KCP_RTO_DEF;
    protected int rx_minrto = KcpBasic.KCP_RTO_MIN;

    protected int snd_wnd = KcpBasic.KCP_WND_SND;
    protected int rcv_wnd = KcpBasic.KCP_WND_RCV;
    protected int rmt_wnd = KcpBasic.KCP_WND_RCV;
    protected int cwnd = 0;
    protected int probe = 0;

    protected int current = 0;
    protected int interval = KcpBasic.KCP_INTERVAL;
    protected int ts_flush = KcpBasic.KCP_INTERVAL;
    protected int xmit = 0;

    protected boolean nodelay = false;
    protected boolean updated = false;

    protected int ts_probe = 0;
    protected int probe_wait = 0;

    protected int dead_link = KcpBasic.KCP_DEADLINK;
    protected int incr = 0;

    protected List<Segment> snd_queue = new ArrayList<>();
    protected List<Segment> rcv_queue = new ArrayList<>();
    protected List<Segment> snd_buf = new ArrayList<>();
    protected List<Segment> rcv_buf = new ArrayList<>();

    protected List<Integer> ackList = new ArrayList<>();

    protected ByteBuf buffer; // outputBuffer
    protected int fastresend = 0;

    protected boolean nocwnd = false; // 是否关闭流控
    protected boolean stream = false; // 流模式

    protected InetSocketAddress user;

    /**
     * 和 tcp 的conv一样，通信双发需要保证conv相同，相互的数据包才能被认可 c->ikcp_create
     *
     * @param conv
     * @param user
     */
    // create a new kcp control object, 'conv' must equal in two endpoint
    // from the same connection. 'user' will be passed to the output callback
    // output callback can be setup like this: 'kcp->output = my_udp_output'
    public Kcp(int conv, InetSocketAddress user, ByteBufAllocator allocator) {
        this.conv = conv;
        this.allocator = allocator;
        this.user = user;
        this.buffer = allocator.buffer((KcpBasic.KCP_MTU_DEF + KcpBasic.KCP_OVERHEAD) * 3);
    }

    /**
     * release bytebufs c->ikcp_release
     */
    // release kcp control object
    public void release() {
        for (Segment seg : snd_buf) {
            seg.release();
        }
        snd_buf.clear();
        for (Segment seg : rcv_buf) {
            seg.release();
        }
        rcv_buf.clear();
        for (Segment seg : snd_queue) {
            seg.release();
        }
        snd_queue.clear();
        for (Segment seg : rcv_queue) {
            seg.release();
        }
        rcv_queue.clear();
        ReferenceCountUtil.release(buffer);
        buffer = null;
    }

    public abstract void output(ByteBuf data);

    /**
     * user/upper level recv: returns size, returns below zero for EAGAIN
     * 将接收队列中的数据传递给上层引用
     *
     * @param buffer
     * @return
     */
    public Optional<ByteBuf> recv() {

        int peekSize = peekSize();
        if (peekSize < 0) {
            return Optional.empty();
        }

        ByteBuf data = allocator.buffer(peekSize);

        boolean recover = false;
        if (rcv_queue.size() >= rcv_wnd) {
            recover = true;
        }

        // merge fragment
        while (!rcv_queue.isEmpty()) {
            Segment seg = rcv_queue.remove(0);
            LOG.trace("recv sn={}", seg.sn);
            seg.encodeData(data);
            seg.release();
            if (seg.frg == 0) {
                break;
            }
        }

        // move available data from rcv_buf -> rcv_queue
        while (!rcv_buf.isEmpty()) {
            Segment peek = rcv_buf.get(0);
            if (peek.sn == this.rcv_nxt && rcv_queue.size() < this.rcv_wnd) {
                rcv_queue.add(rcv_buf.remove(0));
                this.rcv_nxt++;
            } else {
                break;
            }
        }

        // fast recover
        if (rcv_queue.size() < this.rcv_wnd && recover) {
            // ready to send back IKCP_CMD_WINS in ikcp_flush
            // tell remote my window size
            this.probe |= KcpBasic.KCP_ASK_TELL;
        }

        return Optional.of(data);
    }

    /**
     * peek data size
     * 计算接收队列中有多少可用的数据
     *
     * @return
     */
    private int peekSize() {

        if (rcv_queue.isEmpty()) {
            return -1;
        }

        Segment seg = rcv_queue.get(0);
        if (seg.frg == 0) {
            return seg.size();
        }

        if (rcv_queue.size() < seg.frg + 1) {
            return -1;
        }

        int length = 0;
        for (Segment s : rcv_queue) {
            length += s.size();
            if (s.frg == 0) {
                break;
            }
        }

        return length;
    }

    /**
     * user/upper level send
     *
     * @param buffer
     * @return
     */
    public void send(ByteBuf buffer) {

        try {
            int len = buffer.readableBytes();

            if (this.mss <= 0) {
                throw new IllegalArgumentException();
            }

            if (len < 0) {
                return;
            }

            // append to previous segment in streaming mode (if possible)
            if (this.stream && !this.snd_queue.isEmpty()) {
                Segment old = this.snd_queue.get(this.snd_queue.size() - 1);
                if (old.size() < this.mss) {
                    int capacity = this.mss - old.size();
                    int extend = len < capacity ? len : capacity;
                    old.writeData(buffer, extend);
                    old.frg = 0;
                    len -= extend;
                }
            }

            if (len < 0) {
                return;
            }

            int count;
            if (len <= this.mss) {
                count = 1;
            } else {
                count = (len + this.mss - 1) / this.mss;
            }

            if (count > 255) {
                throw new SecurityException("data too int");
            }

            if (count == 0) {
                count = 1;
            }

            // fragment
            for (int i = 0; i < count; i++) {
                int size = len > this.mss ? this.mss : len;
                Segment seg = new Segment(size, allocator);
                seg.writeData(buffer, size);
                seg.frg = stream ? 0 : (count - i - 1);
                snd_queue.add(seg);
            }

        } finally {
            ReferenceCountUtil.release(buffer);
        }
    }

    /**
     * 收到udp协议时调用
     *
     * @param data
     * @return
     */
    public void input(ByteBuf data) {

        int unaTemp = snd_una;
        int maxAck = 0;
        boolean flag = false;

        LOG.trace("[RI] {} bytes", data.readableBytes());

        if (data == null || data.readableBytes() < KcpBasic.KCP_OVERHEAD) {
            return;
        }

        while (true) {

            if (data.readableBytes() < KcpBasic.KCP_OVERHEAD) {
                break;
            }

            int conv = data.readIntLE();
            if (conv != this.conv) {
                throw new SecurityException(String.format("conv not equal: this.conv=%d conv=%d", this.conv, conv));
            }

            int cmd = data.readByte();
            int frg = data.readByte();
            int wnd = data.readShortLE();
            int ts = data.readIntLE();
            int sn = data.readIntLE();
            int una = data.readIntLE();
            int len = data.readIntLE();

            if (cmd != KcpBasic.KCP_CMD_PUSH && cmd != KcpBasic.KCP_CMD_ACK && cmd != KcpBasic.KCP_CMD_WASK
                    && cmd != KcpBasic.KCP_CMD_WINS) {
                throw new IllegalArgumentException("error data : cmd not exits");
            }

            this.rmt_wnd = wnd;
            KcpUtils.parseUna(this, una);
            KcpUtils.shrinkBuf(this);

            switch (cmd) {
                case KcpBasic.KCP_CMD_ACK:
                    if (KcpUtils.timeDiff(this.current, ts) >= 0) {
                        KcpUtils.updateAck(this, KcpUtils.timeDiff(this.current, ts));
                    }
                    KcpUtils.parseAck(this, sn);
                    KcpUtils.shrinkBuf(this);
                    if (!flag) {
                        flag = true;
                        maxAck = sn;
                    } else if (KcpUtils.timeDiff(sn, maxAck) > 0) {
                        maxAck = sn;
                    }
                    LOG.trace(String.format("input ack: sn=%d rtt=%d rto=%d", sn, KcpUtils.timeDiff(this.current, ts),
                            this.rx_rto));
                    break;
                case KcpBasic.KCP_CMD_PUSH:
                    LOG.trace("input psh : snd={} ts={}", sn, ts);
                    if (KcpUtils.timeDiff(sn, this.rcv_nxt + this.rcv_wnd) < 0) {
                        KcpUtils.pushAck(this, sn, ts);
                        if (KcpUtils.timeDiff(sn, this.rcv_nxt) >= 0) {
                            Segment segment = new Segment(len, allocator);
                            segment.conv = conv;
                            segment.cmd = cmd;
                            segment.frg = frg;
                            segment.wnd = wnd;
                            segment.ts = ts;
                            segment.sn = sn;
                            segment.una = una;
                            if (len > 0) {
                                segment.writeData(data, len);
                            }
                            KcpUtils.parseData(this, segment);
                        }
                    }
                    break;
                case KcpBasic.KCP_CMD_WASK:
                    // ready to send back IKCP_CMD_WINS in ikcp_flush
                    // tell remote my window size
                    this.probe |= KcpBasic.KCP_ASK_TELL;
                    LOG.trace("input probe");
                    break;
                case KcpBasic.KCP_CMD_WINS:
                    // do nothing
                    LOG.trace("input wins : %lu", wnd);
                    break;
                default:
                    throw new SecurityException("error data : cmd not exist");
            }
        }

        if (flag) {
            KcpUtils.parseFastAck(this, maxAck);
        }

        if (KcpUtils.timeDiff(this.snd_una, unaTemp) > 0) {
            if (this.cwnd < this.rmt_wnd) {
                int mss = this.mss;
                if (this.cwnd < this.ssthresh) {
                    this.cwnd++;
                    this.incr += mss;
                } else {
                    if (this.incr < mss) {
                        this.incr = mss;
                    }
                    this.incr += (mss * mss) / this.incr + (mss / 16);
                    if ((this.cwnd + 1) * mss <= this.incr) {
                        this.cwnd++;
                    }
                }
                if (this.cwnd > this.rmt_wnd) {
                    this.cwnd = this.rmt_wnd;
                    this.incr = this.rmt_wnd * mss;
                }
            }
        }
    }

    /**
     * c->ikcp_flush
     */
    private void flush() {

        int current = this.current;

        // 'ikcp_update' haven't been called.
        if (!this.updated) {
            return;
        }

        Segment seg = new Segment(0, allocator);
        seg.conv = this.conv;
        seg.cmd = KcpBasic.KCP_CMD_ACK;
        seg.frg = 0;
        seg.wnd = KcpUtils.wndUnused(this);
        seg.una = this.rcv_nxt;
        seg.sn = 0;
        seg.ts = 0;

        // flush acknowledges
        int count = this.ackList.size() / 2;
        for (int i = 0; i < count; i++) {
            if (this.buffer.readableBytes() + KcpBasic.KCP_OVERHEAD > this.mtu) {
                output(buffer);
                this.buffer = allocator.buffer((KcpBasic.KCP_MTU_DEF + KcpBasic.KCP_OVERHEAD) * 3);
            }
            seg.sn = ackList.get(i * 2);
            seg.ts = ackList.get(i * 2 + 1);
            seg.encode(buffer);
        }
        ackList.clear();

        // probe window size (if remote window size equals zero)
        if (this.rmt_wnd == 0) {
            if (this.probe_wait == 0) {
                this.probe_wait = KcpBasic.KCP_PROBE_INIT;
                this.ts_probe = this.current + this.probe_wait;
            } else {
                if (KcpUtils.timeDiff(this.current, this.ts_probe) >= 0) {
                    if (this.probe_wait < KcpBasic.KCP_PROBE_INIT) {
                        this.probe_wait = KcpBasic.KCP_PROBE_INIT;
                    }
                    this.probe_wait += this.probe_wait / 2;
                    if (this.probe_wait > KcpBasic.KCP_PROBE_LIMIT) {
                        this.probe_wait = KcpBasic.KCP_PROBE_LIMIT;
                    }
                    this.ts_probe = this.current + this.probe_wait;
                    this.probe |= KcpBasic.KCP_ASK_SEND;
                }
            }
        } else {
            this.ts_probe = 0;
            this.probe_wait = 0;
        }

        // flush window probing commands
        if ((this.probe & KcpBasic.KCP_ASK_SEND) != 0) {
            seg.cmd = KcpBasic.KCP_CMD_WASK;
            if (this.buffer.readableBytes() + KcpBasic.KCP_OVERHEAD > this.mtu) {
                output(buffer);
                buffer = allocator.buffer((KcpBasic.KCP_MTU_DEF + KcpBasic.KCP_OVERHEAD) * 3);
            }
            seg.encode(buffer);
        }

        // flush window probing commands
        if ((this.probe & KcpBasic.KCP_ASK_TELL) != 0) {
            seg.cmd = KcpBasic.KCP_CMD_WINS;
            if (this.buffer.readableBytes() + KcpBasic.KCP_OVERHEAD > this.mtu) {
                output(buffer);
                buffer = allocator.buffer((KcpBasic.KCP_MTU_DEF + KcpBasic.KCP_OVERHEAD) * 3);
            }
            seg.encode(buffer);
        }

        this.probe = 0;

        // calculate window size
        int cwndTemp = Math.min(this.snd_wnd, this.rmt_wnd);
        if (!this.nocwnd) {
            cwndTemp = Math.min(this.cwnd, cwndTemp);
        }

        // move data from snd_queue to snd_buf
        while (KcpUtils.timeDiff(this.snd_nxt, this.snd_una + cwndTemp) < 0
                && !this.snd_queue.isEmpty()) {
            Segment newSeg = snd_queue.remove(0);
            newSeg.conv = this.conv;
            newSeg.cmd = KcpBasic.KCP_CMD_PUSH;
            newSeg.wnd = seg.wnd;
            newSeg.ts = current;
            newSeg.sn = this.snd_nxt++;
            newSeg.una = this.rcv_nxt;
            newSeg.resendts = current;
            newSeg.rto = this.rx_rto;
            newSeg.fastack = 0;
            newSeg.xmit = 0;
            snd_buf.add(newSeg);
        }

        // calculate resent
        int resent = this.fastresend > 0 ? this.fastresend : 0xffffffff;
        int rtomin = this.nodelay ? 0 : (this.rx_rto >> 3);

        boolean lost = false;
        boolean change = false;
        // flush data segments
        for (Segment segment : this.snd_buf) {
            boolean needSend = false;
            if (segment.xmit == 0) {
                needSend = true;
                segment.xmit++;
                segment.rto = this.rx_rto;
                segment.resendts = current + segment.rto + rtomin;
            } else if (KcpUtils.timeDiff(current, segment.resendts) >= 0) {
                needSend = true;
                segment.xmit++;
                this.xmit++;
                if (this.nodelay) {
                    segment.rto += this.rx_rto / 2;
                } else {
                    segment.rto += this.rx_rto;
                }
                segment.resendts = current + segment.rto;
                lost = true;
            } else if (segment.fastack >= resent) {
                needSend = true;
                segment.xmit++;
                segment.fastack = 0;
                segment.resendts = current + segment.rto;
                change = true;
            }

            if (needSend) {
                segment.ts = current;
                segment.wnd = seg.wnd;
                segment.una = this.rcv_nxt;

                int need = KcpBasic.KCP_OVERHEAD + segment.size();
                if (buffer.readableBytes() + need > this.mtu) {
                    output(buffer);
                    buffer = allocator.buffer((KcpBasic.KCP_MTU_DEF + KcpBasic.KCP_OVERHEAD) * 3);
                }

                segment.encode(buffer);
                if (segment.size() >= 0) {
                    segment.encodeData(buffer);
                }

                if (segment.xmit >= this.dead_link) {
                    this.state = -1;
                }
            }
        }

        // flash remain segments
        if (buffer.readableBytes() > 0) {
            output(buffer);
            buffer = allocator.buffer((KcpBasic.KCP_MTU_DEF + KcpBasic.KCP_OVERHEAD) * 3);
        }

        // update ssthresh
        if (change) {
            int inflight = this.snd_nxt - this.snd_una;
            this.ssthresh = inflight / 2;
            if (this.ssthresh < KcpBasic.KCP_THRESH_MIN) {
                this.ssthresh = KcpBasic.KCP_THRESH_MIN;
            }
            this.cwnd = this.ssthresh + resent;
            this.incr = this.cwnd * this.mss;
        }

        if (lost) {
            this.ssthresh = cwndTemp / 2;
            if (this.ssthresh < KcpBasic.KCP_THRESH_MIN) {
                this.ssthresh = KcpBasic.KCP_THRESH_MIN;
            }
            this.cwnd = 1;
            this.incr = this.mss;
        }

        if (this.cwnd < 1) {
            this.cwnd = 1;
            this.incr = this.mss;
        }

    }

    // ---------------------------------------------------------------------
    // update state (call it repeatedly, every 10ms-100ms), or you can ask
    // ikcp_check when to call it again (without ikcp_input/_send calling).
    // 'current' - current timestamp in millisec.
    // ---------------------------------------------------------------------
    public void update(int current) {

        this.current = current;

        if (!this.updated) {
            this.updated = true;
            this.ts_flush = this.current;
        }

        int slap = KcpUtils.timeDiff(this.current, this.ts_flush);
        if (slap >= 10000 || slap < -10000) {
            this.ts_flush = this.current;
            slap = 0;
        }
        if (slap >= 0) {
            this.ts_flush += this.interval;
            if (KcpUtils.timeDiff(this.current, this.ts_flush) >= 0) {
                this.ts_flush = this.current + this.interval;
            }
            flush();
        }

    }

    // ---------------------------------------------------------------------
    // Determine when should you invoke ikcp_update:
    // returns when you should invoke ikcp_update in millisec, if there
    // is no ikcp_input/_send calling. you can call ikcp_update in that
    // time, instead of call update repeatly.
    // Important to reduce unnacessary ikcp_update invoking. use it to
    // schedule ikcp_update (eg. implementing an epoll-like mechanism,
    // or optimize ikcp_update when handling massive kcp connections)

    /**
     * 管理大规模连接
     * <p>
     * 如果需要同时管理大规模的 KCP连接（比如大于3000个），比如你正在实现一套类 epoll的机制，那么为了避免每秒钟对每个连接调用大量的调用
     * ikcp_update，我们可以使用 ikcp_check 来大大减少 ikcp_update调用的次数。
     * ikcp_check返回值会告诉你需要在什么时间点再次调用 ikcp_update（如果中途没有 ikcp_send,
     * ikcp_input的话，否则中途调用了 ikcp_send, ikcp_input的话，需要在下一次interval时调用 update）
     * <p>
     * 标准顺序是每次调用了 ikcp_update后，使用 ikcp_check决定下次什么时间点再次调用 ikcp_update，而如果中途发生了
     * ikcp_send, ikcp_input 的话，在下一轮 interval 立马调用 ikcp_update和 ikcp_check。
     * 使用该方法，原来在处理2000个 kcp连接且每 个连接每10ms调用一次update，改为 check机制后，cpu从 60%降低到 15%。
     *
     * @param current
     * @return
     */
    public int check(int current) {

        int ts_flush = this.ts_flush;
        int tm_flush = 0x7fffffff;
        int tm_packet = 0x7fffffff;
        int minimal = 0;

        if (!updated) {
            return current;
        }

        if (KcpUtils.timeDiff(current, ts_flush) >= 10000 || KcpUtils.timeDiff(current, ts_flush) < -10000) {
            ts_flush = current;
        }

        if (KcpUtils.timeDiff(current, ts_flush) >= 0) {
            return current;
        }

        tm_flush = KcpUtils.timeDiff(ts_flush, current);

        for (Segment seg : this.snd_buf) {
            int diff = KcpUtils.timeDiff(seg.resendts, current);
            if (diff <= 0) {
                return current;
            }
            if (diff < tm_packet) {
                tm_packet = diff;
            }
        }

        minimal = tm_packet < tm_flush ? tm_packet : tm_flush;
        if (minimal >= this.interval) {
            minimal = this.interval;
        }

        return current + minimal;
    }

    /**
     * 最大传输单元：
     * <p>
     * 纯算法协议并不负责探测 MTU，默认
     * mtu是1400字节，可以使用ikcp_setmtu来设置该值。该值将会影响数据包归并及分片时候的最大传输单元。
     *
     * @param mtu
     */
    public void setMtu(int mtu) {
        if (mtu < 50 || mtu < KcpBasic.KCP_OVERHEAD) {
            throw new IllegalArgumentException("error parm : mtu");
        }

        if (this.buffer != null) {
            ReferenceCountUtil.release(buffer);
        }

        buffer = allocator.buffer((mtu + KcpBasic.KCP_OVERHEAD) * 3);
        this.mtu = mtu;
        mss = mtu - KcpBasic.KCP_OVERHEAD;
    }

    public void setInterval(int interval) {
        if (interval > 5000) {
            interval = 5000;
        } else if (interval < 10) {
            interval = 10;
        }
        this.interval = interval;
    }

    /**
     * 普通模式：`ikcp_nodelay(kcp, 0, 40, 0, 0); 极速模式： ikcp_nodelay(kcp, 1, 10, 2,
     * 1);
     *
     * @param nodelay  是否启用 nodelay模式，0不启用；1启用。
     * @param interval 协议内部工作的 interval，单位毫秒，比如 10ms或者 20ms
     * @param resend   快速重传模式，默认0关闭，可以设置2（2次ACK跨越将会直接重传）
     * @param nc       是否关闭流控，默认是0代表不关闭，1代表关闭。
     */
    public void setNodelay(boolean nodelay, int interval, int resend, boolean nc) {
        this.nodelay = nodelay;
        if (nodelay) {
            this.rx_minrto = KcpBasic.KCP_RTO_NDL;
        } else {
            this.rx_minrto = KcpBasic.KCP_RTO_MIN;
        }

        if (interval >= 0) {
            if (interval > 5000) {
                interval = 5000;
            } else if (interval < 10) {
                interval = 10;
            }
            this.interval = interval;
        }

        if (resend >= 0) {
            this.fastresend = resend;
        }

        this.nocwnd = nc;
    }

    /**
     * 设置协议的最大发送窗口和最大接收窗口大小，默认为32. 这个可以理解为 TCP的 SND_BUF 和 RCV_BUF，只不过单位不一样
     * SND/RCV_BUF 单位是字节，这个单位是包。
     *
     * @param sndwnd
     * @param rcvwnd
     */
    public void setWndSize(int sndwnd, int rcvwnd) {
        if (sndwnd > 0) {
            this.snd_wnd = sndwnd;
        }
        if (rcvwnd > 0) {
            this.rcv_wnd = rcvwnd;
        }
    }

    // get how many packet is waiting to be sent
    public int waitSnd() {
        return this.snd_buf.size() + snd_queue.size();
    }

    public int getConv() {
        return conv;
    }

    public ByteBufAllocator getAllocator() {
        return allocator;
    }


}
