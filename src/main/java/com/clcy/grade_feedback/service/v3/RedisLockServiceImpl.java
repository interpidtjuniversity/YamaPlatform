package com.clcy.grade_feedback.service.v3;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisLockServiceImpl implements RedisLockService{

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 获取分布式锁
     * @param lockKey 锁的键名
     * @param waitTime 等待获取锁的时间（秒）
     * @param leaseTime 锁的租期时间（秒）
     * @return 返回 RLock 对象，如果获取失败则返回 null
     */
    public RLock acquireLock(String lockKey, long waitTime, long leaseTime) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (acquired) {
                return lock;
            } else {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 释放分布式锁
     * @param lock RLock 对象
     */
    public void releaseLock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    public RLock acquireLock(String studentId, int groupId, String examName) {
        String lockKey = String.format("student_group_exam_lock:%s-%d-%s", studentId, groupId, examName);
        RLock lock;
        while (null == (lock = acquireLock(lockKey, 1, 10))) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return lock;
    }
}
