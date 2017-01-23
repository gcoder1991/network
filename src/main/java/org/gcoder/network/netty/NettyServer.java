package org.gcoder.network.netty;


import io.netty.channel.ChannelFuture;
import org.gcoder.network.protocol.TransmissionProtocol;

public interface NettyServer {

    ChannelFuture bind() throws Exception;

    ChannelFuture stop() throws Exception;

    TransmissionProtocol geTransmissionProtocol();
}
