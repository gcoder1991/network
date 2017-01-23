package org.gcoder.network.protocol;

/**
 * Created by gcoder on 2017/1/23.
 */
public abstract class ProtocolProcesser implements Runnable {

    private final int opCode;
    private final byte[] data;

    public ProtocolProcesser(int opCode, byte[] data) {
        this.opCode = opCode;
        this.data = data;
    }

    public int getOpCode() {
        return opCode;
    }

    public byte[] getData() {
        return data;
    }
}
