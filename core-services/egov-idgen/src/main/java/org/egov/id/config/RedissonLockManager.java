package org.egov.id.config;

import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class RedissonLockManager {

    @Autowired
    private RedissonClient redissonClient;

    private final Map<List<String>, RLock> activeLocks = new HashMap<>();


    public boolean lockRecords(List<String> ids) {
        List<RLock> locks = ids.stream()
                .map(id -> redissonClient.getLock("lock:id:" + id))
                .collect(Collectors.toList());

        RLock multiLock = new RedissonMultiLock(locks.toArray(new RLock[0]));

        try {
            boolean acquired = multiLock.tryLock(5, 10, TimeUnit.SECONDS);
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


    public void releaseLocks(List<String> ids) {
        synchronized (activeLocks) {
            RLock lock = activeLocks.remove(ids);
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            } else {
                // fallback unlock individual if needed
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
