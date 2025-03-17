package com.clcy.grade_feedback.service.v3;

import org.redisson.api.RLock;

public interface RedisLockService {

    RLock acquireLock(String lockKey, long waitTime, long leaseTime);

    void releaseLock(RLock lock);

    /**
     * 一定可以取锁成功
     * */
    RLock acquireLock(String studentId, int groupId, String examName);
}
