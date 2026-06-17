package com.clcy.grade_feedback.service.v3;

import org.redisson.api.RLock;

public interface RedisLockService {

    RLock acquireLock(String lockKey, long waitTime, long leaseTime);

    void releaseLock(RLock lock);

    /**
     * 一定可以取锁成功（无限自旋）.
     *
     * 仅适用于关键路径必须拿到锁的场景, 注意调用方需保证锁最终会被释放, 否则线程会一直阻塞.
     * */
    RLock acquireLock(String studentId, int groupId, String examName);

    /**
     * 尝试获取业务锁, 启用看门狗自动续期, 最多重试 maxRetry 次, 失败则返回 null.
     * 适用于后台定时任务等"拿不到就跳过、不能阻塞"的场景, 避免某把锁长期占用导致整个调度线程卡死.
     *
     * @param studentId 学号
     * @param groupId  分组 id
     * @param examName 考试名
     * @param maxRetry 最大重试次数（每次等待 1 秒）, <=0 表示只尝试一次不重试
     * @return 获取成功返回 RLock, 失败返回 null
     */
    RLock tryAcquireLock(String studentId, int groupId, String examName, int maxRetry);
}
