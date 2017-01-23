package org.gcoder.network.kcp.base;

public interface KcpBasic {

    int KCP_RTO_NDL = 30; // no delay min rto
    int KCP_RTO_MIN = 100; // normal min rto
    int KCP_RTO_DEF = 200;
    int KCP_RTO_MAX = 60000;

    int KCP_CMD_PUSH = 81; // cmd: push data
    int KCP_CMD_ACK = 82; // cmd: ack
    int KCP_CMD_WASK = 83; // cmd: window probe (ask)
    int KCP_CMD_WINS = 84; // cmd: window size (tell)

    int KCP_ASK_SEND = 1; // need to send KCP_CMD_WASK
    int KCP_ASK_TELL = 2; // need to send KCP_CMD_WINS

    int KCP_WND_SND = 32;
    int KCP_WND_RCV = 32;

    int KCP_MTU_DEF = 1400;

    int KCP_ACK_FAST = 3;

    int KCP_INTERVAL = 100;
    int KCP_OVERHEAD = 24;
    int KCP_DEADLINK = 20;

    int KCP_THRESH_INIT = 2;
    int KCP_THRESH_MIN = 2;

    int KCP_PROBE_INIT = 7000; // 7 secs to probe window size
    int KCP_PROBE_LIMIT = 120000; // up to 120 secs to probe window
}
