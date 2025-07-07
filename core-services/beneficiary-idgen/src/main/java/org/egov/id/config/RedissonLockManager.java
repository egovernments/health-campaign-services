package org.egov.id.config;

import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * RedissonLockManager handles distributed locking using Redisson MultiLock.
 * It ensures that multiple keys (IDs) can be locked atomically across distributed systems.
 * This is used to prevent concurrent processing of the same IDs.
 */
@Component
public class RedissonLockManager {

    // Injects Redisson client for managing distributed locks
    @Autowired
    private RedissonClient redissonClient;

    // Tracks active multi-locks based on ID lists
    private final Map<List<String>, RLock> activeLocks = new HashMap<>();

    // Time (in seconds) to wait while trying to acquire a lock
    @Value("${multi.lock.wait.time:5}")
    private Integer multiLockWaitTime;

    // Time (in seconds) after which the acquired lock will be automatically released
    @Value("${multi.lock.lease.time:10}")
    private Integer multiLockLeaseTime;

    /**
     * Attempts to acquire a distributed lock on a list of record IDs using Redisson MultiLock.
     * Returns true if the lock is successfully acquired within the configured wait time.
     *
     * @param ids list of unique record IDs to lock
     * @return true if lock is acquired; false otherwise
     */
    public boolean lockRecords(List<String> ids) {
        List<RLock> locks = ids.stream()
                .map(id -> redissonClient.getLock("lock:id:" + id))
                .collect(Collectors.toList());

        RLock multiLock = new RedissonMultiLock(locks.toArray(new RLock[0]));

        try {
            boolean acquired = multiLock.tryLock(multiLockWaitTime, multiLockLeaseTime, TimeUnit.SECONDS);
            if (acquired) {
                synchronized (activeLocks) {
                    activeLocks.put(new ArrayList<>(ids), multiLock);
                }
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Releases the distributed lock for a given list of record IDs.
     * If the multi-lock is not available, it attempts to unlock individual locks as fallback.
     *
     * @param ids list of record IDs whose locks should be released
     */
    public void releaseLocks(List<String> ids) {
        synchronized (activeLocks) {
            RLock lock = activeLocks.remove(ids);
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            } else {
                // fallback to unlocking individual locks if multi-lock is not held
                ids.forEach(id -> {
                    RLock individualLock = redissonClient.getLock("lock:id:" + id);
                    if (individualLock.isHeldByCurrentThread()) {
                        individualLock.unlock();
                    }
                });
            }
        }
    }
}
