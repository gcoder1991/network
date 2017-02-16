package org.gcoder.network.kcp.base;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ReferenceCountUtil;

/**
 * 数据段：
 *      数据包在不同的网络层级中名字有所区别，在链路层，比如以太网，数据叫帧（frame）
 *      往上到IP层叫包（packet），再往上到tcp/kcp叫做段（segment），udp中叫做数据报（datagram）
 */
class Segment {

    int conv;

    int cmd;
    int frg; // fragment
    int wnd;
    int ts;
    int sn;
    int una;

    int resendts;
    int rto;
    int fastack;
    int xmit;

    private ByteBuf data;

    Segment(int size, ByteBufAllocator allocator) {
        if (size > 0) {
            data = allocator.buffer(size);
        }
    }

    void encode(ByteBuf buffer) {
        buffer.writeIntLE(this.conv);
        buffer.writeByte(this.cmd);
        buffer.writeByte(this.frg);
        buffer.writeShortLE(this.wnd);
        buffer.writeIntLE(this.ts);
        buffer.writeIntLE(this.sn);
        buffer.writeIntLE(this.una);
        buffer.writeIntLE(data == null ? 0 : size());
    }

    void encodeData(ByteBuf buffer) {
        buffer.writeBytes(data);
        data.resetReaderIndex();
    }

    void writeData(ByteBuf data, int length) {
        this.data.writeBytes(data, length);
    }

    int size() {
        return data == null ? 0 : this.data.readableBytes();
    }

    void release() {
        if (data != null) {
            ReferenceCountUtil.release(data);
            data = null;
        }
    }

}
