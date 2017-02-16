package org.gcoder.network.kcp.base;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ReferenceCountUtil;

/**
 * 数据段：
 *      数据包在不同的网络层级中名字有所区别，在链路层，比如以太网，数据叫帧（frame）
 *      往上到IP层叫包（packet），再往上到tcp/kcp叫做段（segment），udp中叫做数据报（datagram）
 */
public class Segment {

    protected int conv;

    protected int cmd;
    protected int frg; // fragment
    protected int wnd;
    protected int ts;
    protected int sn;
    protected int una;

    protected int resendts;
    protected int rto;
    protected int fastack;
    protected int xmit;

    private ByteBuf data;

    public Segment(int size, ByteBufAllocator allocator) {
        if (size > 0) {
            data = allocator.buffer(size);
        }
    }

    public void encode(ByteBuf buffer) {
        buffer.writeIntLE(this.conv);
        buffer.writeByte(this.cmd);
        buffer.writeByte(this.frg);
        buffer.writeShortLE(this.wnd);
        buffer.writeIntLE(this.ts);
        buffer.writeIntLE(this.sn);
        buffer.writeIntLE(this.una);
        buffer.writeIntLE(data == null ? 0 : size());
    }

    public void encodeData(ByteBuf buffer) {
        buffer.writeBytes(data);
        data.resetReaderIndex();
    }

    public void writeData(ByteBuf data) {
        this.data.writeBytes(data);
    }

    public void writeData(ByteBuf data, int length) {
        this.data.writeBytes(data, length);
    }

    public int size() {
        return data == null ? 0 : this.data.readableBytes();
    }

    public void release() {
        if (data != null) {
            ReferenceCountUtil.release(data);
            data = null;
        }
    }

}
