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

    /**
     * 获取分布式锁并启用看门狗自动续期.
     * 注意: Redisson 只有在不指定 leaseTime 时才会启动看门狗(按 redisson.yml 的 lockWatchdogTimeout 续期).
     * 之前传了显式 leaseTime=10s 会让看门狗失效, 业务(如 submitExam 后的 generateFeedBackPuzzle)
     * 一旦超过 10s 锁就被自动释放, 导致并发交卷产生重复记录.
     * @param lockKey 锁的键名
     * @param waitTime 等待获取锁的时间（秒）
     * @return 返回 RLock 对象，如果获取失败则返回 null
     */
    public RLock acquireLockWithWatchdog(String lockKey, long waitTime) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(waitTime, TimeUnit.SECONDS);
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

    @Override
    public RLock acquireLock(String studentId, int groupId, String examName) {
        String lockKey = String.format("student_group_exam_lock:%s-%d-%s", studentId, groupId, examName);
        RLock lock;
        // 使用看门狗版本, 锁租期由 Redisson 自动续期, 避免业务执行时间超过固定租期导致锁提前释放.
        // (注: 这里的无限自旋重试属于另一个问题, 此处仅修复看门狗被禁用的回归)
        while (null == (lock = acquireLockWithWatchdog(lockKey, 1))) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return lock;
    }

    @Override
    public RLock tryAcquireLock(String studentId, int groupId, String examName, int maxRetry) {
        String lockKey = String.format("student_group_exam_lock:%s-%d-%s", studentId, groupId, examName);
        // 最多尝试 maxRetry + 1 次, 每次等待 1 秒, 任何一次拿到锁即返回; 全部失败则返回 null.
        int attempts = Math.max(0, maxRetry) + 1;
        for (int i = 0; i < attempts; i++) {
            RLock lock = acquireLockWithWatchdog(lockKey, 1);
            if (null != lock) {
                return lock;
            }
            if (i < attempts - 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }
}
