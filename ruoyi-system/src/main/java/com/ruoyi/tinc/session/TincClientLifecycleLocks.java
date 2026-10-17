package com.ruoyi.tinc.session;

import java.util.concurrent.locks.ReentrantLock;

/** Serializes peer-host writes and node revocation within the single Management process. */
public final class TincClientLifecycleLocks {
    private static final ReentrantLock[] LOCKS = new ReentrantLock[256];
    static {
        for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = new ReentrantLock();
    }

    private TincClientLifecycleLocks() { }

    public static ReentrantLock forNode(Long nodeId) {
        if (nodeId == null || nodeId <= 0) throw new IllegalArgumentException("节点 ID 无效");
        return LOCKS[Math.floorMod(nodeId.hashCode(), LOCKS.length)];
    }
}
