package org.egov.web.notification.push.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;

import jakarta.annotation.PostConstruct;

@Component
public class FcmRateLimiter {

    @Value("${fcm.rate-limit.per-second:5000}")
    private double defaultPerSecond;

    @Value("${fcm.rate-limit-by-project-json:{}}")
    private String byProjectJson;

    private Map<String, Double> byProject = Map.of();
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            byProject = new ObjectMapper().readValue(byProjectJson == null || byProjectJson.isBlank() ? "{}" : byProjectJson,
                    new TypeReference<Map<String, Double>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("FCM_RATE_LIMIT_BY_PROJECT_JSON must be a JSON object like {\"<project_id>\": 2000} (sends per second)");
        }
    }

    public boolean tryAcquire(String projectId, int permits, long timeoutMs) {
        if (System.currentTimeMillis() < blockedUntil.getOrDefault(projectId, 0L)) {
            return false;
        }
        RateLimiter limiter = limiters.computeIfAbsent(projectId,
                id -> RateLimiter.create(byProject.getOrDefault(id, defaultPerSecond)));
        return limiter.tryAcquire(permits, Math.max(0, timeoutMs), TimeUnit.MILLISECONDS);
    }

    public void backOff(String projectId, long millis) {
        if (millis > 0) {
            blockedUntil.merge(projectId, System.currentTimeMillis() + millis, Math::max);
        }
    }

}
