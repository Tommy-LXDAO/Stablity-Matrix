package com.stability.martrix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis分布式锁服务
 * 使用方可通过字符串key判断是否已有其他人加了锁
 */
@Service
public class DistributedLockService {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockService.class);

    /**
     * 锁前缀
     */
    private static final String LOCK_PREFIX = "lock:";

    private final RedisTemplate<String, Object> redisTemplate;

    public DistributedLockService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey 锁的key（字符串）
     * @param expireSeconds 锁过期时间（秒）
     * @return 锁的value（用于释放锁），如果加锁失败返回null
     */
    public String tryLock(String lockKey, long expireSeconds) {
        String actualKey = LOCK_PREFIX + lockKey;
        String lockValue = UUID.randomUUID().toString();

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(actualKey, lockValue, Duration.ofSeconds(expireSeconds));

        if (Boolean.TRUE.equals(success)) {
            logger.info("成功获取分布式锁: {}", lockKey);
            return lockValue;
        } else {
            logger.info("获取分布式锁失败，已被其他进程持有: {}", lockKey);
            return null;
        }
    }

    /**
     * 阻塞获取分布式锁（会一直等待直到获取到锁）
     *
     * @param lockKey 锁的key（字符串）
     * @param expireSeconds 锁过期时间（秒）
     *Ms 重 @param retryInterval试间隔（毫秒）
     * @return 锁的value（用于释放锁）
     */
    public String lock(String lockKey, long expireSeconds, long retryIntervalMs) {
        String lockValue;
        while ((lockValue = tryLock(lockKey, expireSeconds)) == null) {
            try {
                Thread.sleep(retryIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("获取分布式锁被中断: " + lockKey, e);
            }
        }
        return lockValue;
    }

    /**
     * 阻塞获取分布式锁（默认重试间隔100ms）
     *
     * @param lockKey 锁的key（字符串）
     * @param expireSeconds 锁过期时间（秒）
     * @return 锁的value（用于释放锁）
     */
    public String lock(String lockKey, long expireSeconds) {
        return lock(lockKey, expireSeconds, 100);
    }

    /**
     * 释放分布式锁
     *
     * @param lockKey 锁的key（字符串）
     * @param lockValue 加锁时返回的value
     * @return 是否释放成功
     */
    public boolean releaseLock(String lockKey, String lockValue) {
        String actualKey = LOCK_PREFIX + lockKey;

        // 使用Lua脚本确保原子性：只有持有锁的进程才能释放
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        Long result = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, Long.class),
                java.util.Collections.singletonList(actualKey),
                lockValue
        );

        if (result != null && result > 0) {
            logger.info("成功释放分布式锁: {}", lockKey);
            return true;
        } else {
            logger.warn("释放分布式锁失败，锁已被释放或被其他进程持有: {}", lockKey);
            return false;
        }
    }

    /**
     * 检查锁是否已被持有
     *
     * @param lockKey 锁的key（字符串）
     * @return true表示锁已被持有，false表示未被持有
     */
    public boolean isLocked(String lockKey) {
        String actualKey = LOCK_PREFIX + lockKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(actualKey));
    }

    /**
     * 强制删除锁（用于异常情况下的锁清理）
     *
     * @param lockKey 锁的key（字符串）
     */
    public void forceUnlock(String lockKey) {
        String actualKey = LOCK_PREFIX + lockKey;
        redisTemplate.delete(actualKey);
        logger.info("强制删除分布式锁: {}", lockKey);
    }
}
