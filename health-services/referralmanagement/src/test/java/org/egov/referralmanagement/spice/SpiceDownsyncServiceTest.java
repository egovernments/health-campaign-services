package org.egov.referralmanagement.spice;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.referralmanagement.spice.model.SpiceHousehold;
import org.egov.referralmanagement.spice.model.SpiceMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpiceDownsyncService — live orchestration & grouping")
class SpiceDownsyncServiceTest {

    @Mock
    private SpiceApiClient client;

    private SpiceDownsyncService service;

    @BeforeEach
    void setUp() {
        // real mapper, mocked live client
        service = new SpiceDownsyncService(client, new SpiceMapper());
    }

    private SpiceHousehold hh(String id) {
        SpiceHousehold h = new SpiceHousehold();
        h.setId(id);
        h.setNoOfPeople(1);
        return h;
    }

    private SpiceMember mem(String id, String householdId, String rel) {
        SpiceMember m = new SpiceMember();
        m.setId(id);
        m.setHouseholdId(householdId);
        m.setName("A B");
        m.setGender("male");
        m.setHouseholdHeadRelationship(rel);
        return m;
    }

    private DownsyncRequest request(String locality, String tenantId, Long lastSynced) {
        DownsyncCriteria c = DownsyncCriteria.builder()
                .locality(locality).tenantId(tenantId).lastSyncedTime(lastSynced).build();
        return DownsyncRequest.builder()
                .requestInfo(RequestInfo.builder().build())
                .downsyncCriteria(c).build();
    }

    @Test
    @DisplayName("populates Households/Individuals/HouseholdMembers; leaves non-Spice groups null")
    void populatesOnlySpiceGroups() {
        when(client.getHouseholds(eq(64L), eq(123L))).thenReturn(List.of(hh("H1"), hh("H2")));
        when(client.getMembers(eq(64L), eq(123L))).thenReturn(List.of(mem("M1", "H1", "HouseholdHead")));

        Downsync d = service.prepareDownsyncData(request("SL_C1_D16_CH24_V64", "ba", 123L));

        assertEquals(2, d.getHouseholds().size());
        assertEquals(1, d.getIndividuals().size());
        assertEquals(1, d.getHouseholdMembers().size());
        assertNull(d.getProjectBeneficiaries());
        assertNull(d.getTasks());
        assertNull(d.getSideEffects());
        assertNull(d.getReferrals());
        assertNull(d.getHfReferrals());
        assertNull(d.getServices());
    }

    @Test
    @DisplayName("parses villageId from locality code and derives ward code")
    void parsesLocality() {
        when(client.getHouseholds(eq(64L), any())).thenReturn(List.of(hh("H1")));
        when(client.getMembers(eq(64L), any())).thenReturn(List.of(mem("M1", "H1", "Son / Daughter")));

        Downsync d = service.prepareDownsyncData(request("SL_C1_D16_CH24_V64", "ba", null));

        verify(client).getHouseholds(eq(64L), isNull());
        verify(client).getMembers(eq(64L), isNull());
        // locality on household, ward (chiefdom) on individual
        assertEquals("SL_C1_D16_CH24_V64", d.getHouseholds().get(0).getAddress().getLocality().getCode());
        assertEquals("SL_C1_D16_CH24", d.getIndividuals().get(0).getAddress().get(0).getWard().getCode());
    }

    @Test
    @DisplayName("null/blank locality -> defaults to village 64 and default locality code")
    void defaultsWhenNoLocality() {
        when(client.getHouseholds(eq(64L), any())).thenReturn(List.of(hh("H1")));
        when(client.getMembers(eq(64L), any())).thenReturn(List.of());

        Downsync d = service.prepareDownsyncData(request(null, "ba", null));

        verify(client).getHouseholds(eq(64L), isNull());
        assertEquals("SL_C1_D16_CH24_V64", d.getHouseholds().get(0).getAddress().getLocality().getCode());
    }

    @Test
    @DisplayName("locality with a different village id is honored")
    void honorsOtherVillage() {
        when(client.getHouseholds(eq(99L), any())).thenReturn(List.of());
        when(client.getMembers(eq(99L), any())).thenReturn(List.of());

        service.prepareDownsyncData(request("SL_C1_D16_CH24_V99", "ba", null));

        verify(client).getHouseholds(eq(99L), isNull());
        verify(client).getMembers(eq(99L), isNull());
    }

    @Test
    @DisplayName("locality without _V segment -> falls back to default village 64")
    void malformedLocalityFallsBack() {
        when(client.getHouseholds(eq(64L), any())).thenReturn(List.of());
        when(client.getMembers(eq(64L), any())).thenReturn(List.of());

        service.prepareDownsyncData(request("SOME_RANDOM_CODE", "ba", null));

        verify(client).getHouseholds(eq(64L), isNull());
    }

    @Test
    @DisplayName("totalCount = household count; tenant propagated to mapped records")
    void totalCountAndTenant() {
        when(client.getHouseholds(anyLong(), any())).thenReturn(List.of(hh("H1"), hh("H2"), hh("H3")));
        when(client.getMembers(anyLong(), any())).thenReturn(List.of(mem("M1", "H1", "HouseholdHead")));

        Downsync d = service.prepareDownsyncData(request("SL_C1_D16_CH24_V64", "tenantX", null));

        assertEquals(3L, d.getDownsyncCriteria().getTotalCount());
        assertEquals("tenantX", d.getHouseholds().get(0).getTenantId());
        assertEquals("tenantX", d.getIndividuals().get(0).getTenantId());
        assertEquals("tenantX", d.getHouseholdMembers().get(0).getTenantId());
    }

    @Test
    @DisplayName("empty Spice results -> empty populated lists, totalCount 0, others null")
    void emptyResults() {
        when(client.getHouseholds(anyLong(), any())).thenReturn(List.of());
        when(client.getMembers(anyLong(), any())).thenReturn(List.of());

        Downsync d = service.prepareDownsyncData(request("SL_C1_D16_CH24_V64", "ba", null));

        assertTrue(d.getHouseholds().isEmpty());
        assertTrue(d.getIndividuals().isEmpty());
        assertTrue(d.getHouseholdMembers().isEmpty());
        assertEquals(0L, d.getDownsyncCriteria().getTotalCount());
        assertNull(d.getReferrals());
    }

    @Test
    @DisplayName("member with no householdId -> Individual emitted, HouseholdMember skipped")
    void skipsMemberWithoutHousehold() {
        when(client.getHouseholds(anyLong(), any())).thenReturn(List.of(hh("H1")));
        when(client.getMembers(anyLong(), any()))
                .thenReturn(List.of(mem("M1", "H1", "HouseholdHead"), mem("M9", null, null)));

        Downsync d = service.prepareDownsyncData(request("SL_C1_D16_CH24_V64", "ba", null));

        assertEquals(2, d.getIndividuals().size(), "both persons become Individuals");
        assertEquals(1, d.getHouseholdMembers().size(), "orphan (no household) HouseholdMember is skipped");
        assertEquals("M1", d.getHouseholdMembers().get(0).getClientReferenceId());
    }

    @Test
    @DisplayName("mapped records carry rowVersion and auditDetails (DIGIT fields)")
    void populatesAuditAndRowVersion() {
        when(client.getHouseholds(anyLong(), any())).thenReturn(List.of(hh("H1")));
        when(client.getMembers(anyLong(), any())).thenReturn(List.of(mem("M1", "H1", "HouseholdHead")));

        Downsync d = service.prepareDownsyncData(request("SL_C1_D16_CH24_V64", "ba", null));

        assertEquals(1, d.getHouseholds().get(0).getRowVersion());
        assertNotNull(d.getHouseholds().get(0).getAuditDetails());
        assertNotNull(d.getHouseholds().get(0).getAuditDetails().getLastModifiedTime());
        assertNotNull(d.getIndividuals().get(0).getAuditDetails());
        assertNotNull(d.getHouseholdMembers().get(0).getAuditDetails());
    }

    @Test
    @DisplayName("lastSyncedTime is passed through to the Spice client (incremental)")
    void passesLastSyncedTime() {
        when(client.getHouseholds(eq(64L), eq(999L))).thenReturn(List.of());
        when(client.getMembers(eq(64L), eq(999L))).thenReturn(List.of());

        service.prepareDownsyncData(request("SL_C1_D16_CH24_V64", "ba", 999L));

        verify(client).getHouseholds(eq(64L), eq(999L));
        verify(client).getMembers(eq(64L), eq(999L));
    }
}
