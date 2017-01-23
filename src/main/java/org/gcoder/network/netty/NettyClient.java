package org.gcoder.network.netty;

import io.netty.channel.ChannelFuture;
import org.gcoder.network.protocol.TransmissionProtocol;

/**
 * Created by gcoder on 2017/1/23.
 */
public interface NettyClient {
    ChannelFuture connect() throws Exception;

    ChannelFuture stop() throws Exception;

    TransmissionProtocol geTransmissionProtocol();
}
