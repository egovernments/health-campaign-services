package org.egov.facility.service.enrichment;


import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.facility.config.FacilityConfiguration;
import org.egov.facility.helper.FacilityBulkRequestTestBuilder;
import org.egov.facility.helper.FacilityTestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class FacilityEnrichmentServiceTest {

    @InjectMocks
    FacilityEnrichmentService facilityEnrichmentService;

    @Mock
    IdGenService idGenService;

    @Mock
    FacilityConfiguration facilityConfiguration;


    @BeforeEach
    void setUp() throws Exception {
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                        any(String.class),
                        eq("facility.id"), eq(""), anyInt()))
                .thenReturn(idList);

        lenient().when(facilityConfiguration.getFacilityIdFormat()).thenReturn("facility.id");
    }

    @Test
    @DisplayName("should generate and set ID using ID generation service")
    void shouldGenerateAndSetIdFromIdGenService() throws Exception {
        FacilityBulkRequest facilityBulkRequest = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo()
                .build();
        List<Facility> facility = facilityBulkRequest.getFacilities();
        facilityEnrichmentService.create(facility, facilityBulkRequest);

        assertNotNull(facility.get(0).getId());
        verify(idGenService, times(1))
                .getIdList(any(RequestInfo.class), anyString(), eq("facility.id"), eq(""), anyInt());
    }

    @Test
    @DisplayName("should enrich facility request with rowVersion and isDeleted")
    void shouldEnrichFacilityWithRowVersionAndIsDeleted() throws Exception {
        FacilityBulkRequest facilityBulkRequest = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo()
                .build();

        List<Facility> facility = facilityBulkRequest.getFacilities();
        facilityEnrichmentService.create(facility, facilityBulkRequest);

        assertEquals(1, facility.stream().findAny().get().getRowVersion());
        assertFalse(facility.stream().findAny().get().getIsDeleted());
    }

    @Test
    @DisplayName("should enrich facility request with audit details")
    void shouldEnrichFacilityWithAuditDetails() throws Exception {
        FacilityBulkRequest facilityBulkRequest = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo()
                .build();

        List<Facility> facility = facilityBulkRequest.getFacilities();
        facilityEnrichmentService.create(facility, facilityBulkRequest);

        assertNotNull(facility.stream().findAny().get().getAuditDetails().getCreatedBy());
        assertNotNull(facility.stream().findAny().get().getAuditDetails().getCreatedTime());
        assertNotNull(facility.stream().findAny().get().getAuditDetails().getLastModifiedBy());
        assertNotNull(facility.stream().findAny().get().getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should generate address Id where address is not null")
    void shouldEnrichAddressIdIfAddressNotNull() throws Exception {
        Facility withAddress = FacilityTestBuilder.builder().withFacility().build();
        FacilityBulkRequest facilityBulkRequest = FacilityBulkRequestTestBuilder.builder()
                .withFacility(Arrays.asList(withAddress)).withRequestInfo()
                .build();

        List<Facility> facility = facilityBulkRequest.getFacilities();
        facilityEnrichmentService.create(facility, facilityBulkRequest);

        assertNotNull(facility.get(0).getAddress().getId());
    }

    @Test
    @DisplayName("should not generate address Id where address is null")
    void shouldNotEnrichAddressIdIfAddressNull() throws Exception {
        Facility withNullAddress = FacilityTestBuilder.builder().withFacility().withAddress(null).build();
        FacilityBulkRequest facilityBulkRequest = FacilityBulkRequestTestBuilder.builder()
                .withFacility(Arrays.asList(withNullAddress)).withRequestInfo()
                .build();
        List<Facility> facility = facilityBulkRequest.getFacilities();
        facilityEnrichmentService.create(facility, facilityBulkRequest);

        assertNull(facility.get(0).getAddress());
        verify(idGenService, times(1))
                .getIdList(any(RequestInfo.class), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("should update audit details for facility update")
    void shouldUpdateAuditDetailsForFacilityUpdate() throws Exception {
        FacilityBulkRequest facilityBulkRequest = FacilityBulkRequestTestBuilder.builder()
                .withFacility()
                .withRequestInfo()
                .build();
        facilityBulkRequest.getFacilities().get(0).setAuditDetails(null);

        facilityEnrichmentService
                .update(facilityBulkRequest.getFacilities(), facilityBulkRequest);

        assertNotNull(facilityBulkRequest.getFacilities().stream().findAny()
                .get().getAuditDetails());
    }

    @Test
    @DisplayName("should set is deleted true for facility")
    void shouldSetIsDeletedTrueForFacility() throws Exception {
        FacilityBulkRequest facilityBulkRequest = FacilityBulkRequestTestBuilder.builder()
                .withFacility()
                .withRequestInfo()
                .build();


        facilityEnrichmentService
                .delete(facilityBulkRequest.getFacilities(), facilityBulkRequest);

        assertTrue(facilityBulkRequest.getFacilities().get(0).getIsDeleted());
    }

}
