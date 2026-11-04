package org.egov.web.notification.push.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PushPropertiesTest {

    private static PushProperties properties(String ttlJson) {
        PushProperties p = new PushProperties();
        ReflectionTestUtils.setField(p, "defaultTtlSeconds", 21600L);
        ReflectionTestUtils.setField(p, "ttlByTypeJson", ttlJson);
        p.parseTtlByType();
        return p;
    }

    @Test
    void ttl_usesTypeOverrideOrDefault() {
        PushProperties p = properties("{\"STOCK_ISSUE\": 3600}");

        assertEquals(3600, p.ttlSeconds("STOCK_ISSUE"));
        assertEquals(21600, p.ttlSeconds("OTHER"));
        assertEquals(21600, p.ttlSeconds(null));
    }

    @Test
    void ttl_blankJsonMeansDefaults() {
        assertEquals(21600, properties("").ttlSeconds("STOCK_ISSUE"));
    }

    @Test
    void ttl_invalidJsonFailsStartup() {
        assertThrows(IllegalStateException.class, () -> properties("[1,2]"));
    }

    @Test
    void statePrefix_stripsKnownBaseTopic() {
        PushProperties p = new PushProperties();
        ReflectionTestUtils.setField(p, "pushNotificationTopic", "egov.core.notification.push");
        ReflectionTestUtils.setField(p, "retryTopic1", "notification-push-retry-1m");
        ReflectionTestUtils.setField(p, "retryTopic2", "notification-push-retry-5m");
        ReflectionTestUtils.setField(p, "retryTopic3", "notification-push-retry-30m");

        assertEquals("ba-", p.statePrefix("ba-egov.core.notification.push"));
        assertEquals("ng-", p.statePrefix("ng-notification-push-retry-5m"));
        assertEquals("", p.statePrefix("egov.core.notification.push"));
    }
}
