package org.egov.web.notification.push.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PushMetricsTest {

    @Test
    void countersAreTaggedByStateLevelTenant() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PushMetrics metrics = new PushMetrics();
        ReflectionTestUtils.setField(metrics, "registry", registry);

        metrics.sent("ng.kano.ward1", "p1", null, 3);
        metrics.sent("ng.lagos", "p1", null, 2);
        metrics.failed(null, "p1", "temporary", "INTERNAL", 1);

        assertEquals(5, registry.get("push.sent").tag("tenant", "ng").tag("project", "p1").tag("type", "none").counter().count());
        assertEquals(1, registry.get("push.failed").tag("tenant", "none").tag("code", "INTERNAL").counter().count());
    }
}
