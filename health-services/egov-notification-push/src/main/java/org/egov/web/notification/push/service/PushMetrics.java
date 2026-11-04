package org.egov.web.notification.push.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

@Component
public class PushMetrics {

    @Autowired
    private MeterRegistry registry;

    public void sent(String tenantId, String projectId, String type, int count) {
        if (count > 0) {
            registry.counter("push.sent", "tenant", state(tenantId), "project", nz(projectId), "type", nz(type)).increment(count);
        }
    }

    public void failed(String tenantId, String projectId, String failureClass, String code, int count) {
        registry.counter("push.failed", "tenant", state(tenantId), "project", nz(projectId), "class", failureClass, "code", nz(code))
                .increment(count);
    }

    public void retried(String tenantId, int attempt, int tokens) {
        registry.counter("push.retry", "tenant", state(tenantId), "attempt", String.valueOf(attempt)).increment(tokens);
    }

    public void deadLettered(String tenantId, String reason, int count) {
        registry.counter("push.deadletter", "tenant", state(tenantId), "reason", nz(reason)).increment(count);
    }

    public void noRecipients(String tenantId) {
        registry.counter("push.no.recipients", "tenant", state(tenantId)).increment();
    }

    public void sendLatency(String projectId, Duration duration) {
        registry.timer("push.send.latency", "project", nz(projectId)).record(duration);
    }

    static String state(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return "none";
        }
        int dot = tenantId.indexOf('.');
        return dot > 0 ? tenantId.substring(0, dot) : tenantId;
    }

    private static String nz(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

}
