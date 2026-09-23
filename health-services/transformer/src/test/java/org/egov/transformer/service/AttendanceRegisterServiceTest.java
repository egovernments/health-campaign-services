package org.egov.transformer.service;

import org.egov.transformer.config.TransformerProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttendanceRegisterServiceTest {

    private static final String CM_UUID = "d687a85c-85c4-4425-8326-2829706c1b47";

    private AttendanceRegisterService serviceWith(String config) {
        TransformerProperties properties = new TransformerProperties();
        properties.setAttendanceRegisterSearchUserUuid(config);
        return new AttendanceRegisterService(properties, null, null, null, null);
    }

    @Test
    void shouldUseConfiguredUuidForMatchingTenant() {
        assertEquals(CM_UUID, serviceWith("bo:" + CM_UUID)
                .resolveSearchUserUuid("bo", "testing-staff-enroll"));
    }

    @Test
    void shouldFallBackToCreatedByForUnconfiguredTenant() {
        assertEquals("ba-creator-uuid", serviceWith("bo:" + CM_UUID)
                .resolveSearchUserUuid("ba", "ba-creator-uuid"));
    }

    @Test
    void shouldFallBackToCreatedByWhenConfigIsEmpty() {
        assertEquals("testing-staff-enroll", serviceWith("")
                .resolveSearchUserUuid("bo", "testing-staff-enroll"));
        assertEquals("testing-staff-enroll", serviceWith(null)
                .resolveSearchUserUuid("bo", "testing-staff-enroll"));
    }

    @Test
    void shouldParseMultipleTenantPairs() {
        AttendanceRegisterService service = serviceWith("bo:" + CM_UUID + " , ke:ke-uuid");
        assertEquals(CM_UUID, service.resolveSearchUserUuid("bo", "x"));
        assertEquals("ke-uuid", service.resolveSearchUserUuid("ke", "x"));
        assertEquals("x", service.resolveSearchUserUuid("na", "x"));
    }
}
