package org.gcoder.session;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.gcoder.network.kcp.KcpSession;
import org.gcoder.network.kcp.KcpSessionManager;
import org.gcoder.network.kcp.base.Kcp;

/**
 * Created by gcoder on 2017/2/10.
 */
public class DefaultSession extends KcpSession {

    public DefaultSession(Kcp kcp, KcpSessionManager sessionManager) {
        super(kcp, sessionManager);
    }

    @Override
    public void recevieProtocol(ByteBuf data) {
        System.out.println(new String(ByteBufUtil.getBytes(data).toString()));
    }

}
