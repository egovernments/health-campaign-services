package org.egov.facility.service;

import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.models.facility.FacilityRequest;
import org.egov.common.validator.Validator;
import org.egov.facility.config.FacilityConfiguration;
import org.egov.facility.helper.FacilityBulkRequestTestBuilder;
import org.egov.facility.helper.FacilityRequestTestBuilder;
import org.egov.facility.helper.FacilityTestBuilder;
import org.egov.facility.repository.FacilityRepository;
import org.egov.facility.service.enrichment.FacilityEnrichmentService;
import org.egov.facility.validator.FIsDeletedValidator;
import org.egov.facility.validator.FNonExistentValidator;
import org.egov.facility.validator.FNullIdValidator;
import org.egov.facility.validator.FRowVersionValidator;
import org.egov.facility.validator.FUniqueEntityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityServiceTest {

    @Spy
    @InjectMocks
    private FacilityService facilityService;

    @Mock
    private FacilityRepository repository;

    @Mock
    private FIsDeletedValidator fIsDeletedValidator;

    @Mock
    private FNonExistentValidator fNonExistentValidator;

    @Mock
    private FNullIdValidator fNullIdValidator;

    @Mock
    private FRowVersionValidator fRowVersionValidator;

    @Mock
    private FUniqueEntityValidator fUniqueEntityValidator;

    @Mock
    private FacilityEnrichmentService enrichmentService;

    @Mock
    private FacilityConfiguration configuration;

    List<Validator<FacilityBulkRequest, Facility>> validators;

    @BeforeEach
    void setUp() {
        validators = Arrays.asList(fIsDeletedValidator, fNonExistentValidator, fNullIdValidator, 
                fRowVersionValidator, fUniqueEntityValidator);
        ReflectionTestUtils.setField(facilityService, "validators", validators);

        lenient().when(configuration.getCreateFacilityTopic()).thenReturn("create-facility-topic");
        lenient().when(configuration.getUpdateFacilityTopic()).thenReturn("update-facility-topic");
        lenient().when(configuration.getDeleteFacilityTopic()).thenReturn("delete-facility-topic");
    }


    @Test
    @DisplayName("should call create with isBulk false")
    void shouldCallCreateWithIsBulkFalse() {
        FacilityRequest request = FacilityRequestTestBuilder.builder().withFacility().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getFacility()))
                .when(facilityService).create(any(FacilityBulkRequest.class), anyBoolean());

        facilityService.create(request);

        verify(facilityService, times(1)).create(any(FacilityBulkRequest.class), eq(false));
    }

    @Test
    @DisplayName("should call update with isBulk false")
    void shouldCallUpdateWithIsBulkFalse() {
        FacilityRequest request = FacilityRequestTestBuilder.builder().withFacility().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getFacility()))
                .when(facilityService).update(any(FacilityBulkRequest.class), anyBoolean());

        facilityService.update(request);

        verify(facilityService, times(1)).update(any(FacilityBulkRequest.class), eq(false));
    }

    @Test
    @DisplayName("should call delete with isBulk false")
    void shouldCallDeleteWithIsBulkFalse() {
        FacilityRequest request = FacilityRequestTestBuilder.builder().withFacility().withRequestInfo().build();
        doReturn(Collections.singletonList(request.getFacility()))
                .when(facilityService).delete(any(FacilityBulkRequest.class), anyBoolean());

        facilityService.delete(request);

        verify(facilityService, times(1)).delete(any(FacilityBulkRequest.class), eq(false));
    }

    @Test
    @DisplayName("should call kafka topic if valid facility found for create")
    void shouldCallKafkaTopicCreate() {
        FacilityBulkRequest facilityBulkRequest = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();

        List<Facility> facility = facilityService.create(facilityBulkRequest, false);

        assertEquals(1, facility.size());
        verify(repository, times(1)).save(anyList(), eq("create-facility-topic"));
    }

    private Facility facilityWithClientReferenceId(String clientReferenceId) {
        Facility facility = FacilityTestBuilder.builder().withFacility().build();
        facility.setClientReferenceId(clientReferenceId);
        return facility;
    }

    private Facility savedFacility(String id, String clientReferenceId, String tenantId) {
        Facility facility = FacilityTestBuilder.builder().withFacility().withId(id).build();
        facility.setClientReferenceId(clientReferenceId);
        facility.setTenantId(tenantId);
        return facility;
    }

    @Test
    @DisplayName("should return the saved facility and not save again when its clientReferenceId is already saved")
    void shouldReturnSavedFacilityWhenClientReferenceIdAlreadySaved() throws Exception {
        when(configuration.getReturnExistingOnDuplicateClientReferenceId()).thenReturn(true);
        Facility saved = savedFacility("F-1", "crid-1", "default");
        when(repository.findByClientReferenceIdFromDb(eq("default"), eq(List.of("crid-1")))).thenReturn(List.of(saved));
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder()
                .withFacility(new ArrayList<>(List.of(facilityWithClientReferenceId("crid-1")))).withRequestInfo().build();

        List<Facility> result = facilityService.create(request, false);

        assertEquals(1, result.size());
        assertSame(saved, result.get(0));
        verify(enrichmentService, never()).create(anyList(), any(FacilityBulkRequest.class));
        verify(repository, never()).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should create only the new facilities and keep request order when some are already saved")
    void shouldCreateOnlyNewFacilitiesWhenSomeAreAlreadySaved() throws Exception {
        when(configuration.getReturnExistingOnDuplicateClientReferenceId()).thenReturn(true);
        Facility saved = savedFacility("F-1", "crid-1", "default");
        when(repository.findByClientReferenceIdFromDb(eq("default"), anyList())).thenReturn(List.of(saved));
        Facility repeated = facilityWithClientReferenceId("crid-1");
        Facility fresh = facilityWithClientReferenceId("crid-2");
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder()
                .withFacility(new ArrayList<>(List.of(fresh, repeated))).withRequestInfo().build();

        List<Facility> result = facilityService.create(request, false);

        assertEquals(2, result.size());
        assertSame(fresh, result.get(0));
        assertSame(saved, result.get(1));
        ArgumentCaptor<List<Facility>> created = ArgumentCaptor.forClass(List.class);
        verify(repository, times(1)).save(created.capture(), eq("create-facility-topic"));
        assertEquals(List.of(fresh), created.getValue());
        verify(enrichmentService, times(1)).create(eq(List.of(fresh)), any(FacilityBulkRequest.class));
    }

    @Test
    @DisplayName("should create as before without looking up when the switch is off")
    void shouldCreateAsBeforeWhenSwitchIsOff() throws Exception {
        when(configuration.getReturnExistingOnDuplicateClientReferenceId()).thenReturn(false);
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder()
                .withFacility(new ArrayList<>(List.of(facilityWithClientReferenceId("crid-1")))).withRequestInfo().build();

        List<Facility> result = facilityService.create(request, false);

        assertEquals(1, result.size());
        verify(repository, never()).findByClientReferenceIdFromDb(anyString(), anyList());
        verify(repository, times(1)).save(anyList(), eq("create-facility-topic"));
    }

    @Test
    @DisplayName("should not look up when no facility has a clientReferenceId")
    void shouldNotLookUpWithoutClientReferenceId() throws Exception {
        when(configuration.getReturnExistingOnDuplicateClientReferenceId()).thenReturn(true);
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();

        facilityService.create(request, false);

        verify(repository, never()).findByClientReferenceIdFromDb(anyString(), anyList());
        verify(repository, times(1)).save(anyList(), eq("create-facility-topic"));
    }

    @Test
    @DisplayName("should not return a saved facility of another tenant")
    void shouldNotReturnSavedFacilityOfAnotherTenant() throws Exception {
        when(configuration.getReturnExistingOnDuplicateClientReferenceId()).thenReturn(true);
        when(repository.findByClientReferenceIdFromDb(anyString(), anyList()))
                .thenReturn(List.of(savedFacility("F-9", "crid-1", "other")));
        Facility facility = facilityWithClientReferenceId("crid-1");
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder()
                .withFacility(new ArrayList<>(List.of(facility))).withRequestInfo().build();

        List<Facility> result = facilityService.create(request, false);

        assertSame(facility, result.get(0));
        verify(repository, times(1)).save(anyList(), eq("create-facility-topic"));
    }

    @Test
    @DisplayName("should create as before when the lookup fails")
    void shouldCreateAsBeforeWhenLookupFails() throws Exception {
        when(configuration.getReturnExistingOnDuplicateClientReferenceId()).thenReturn(true);
        when(repository.findByClientReferenceIdFromDb(anyString(), anyList())).thenThrow(new RuntimeException("db down"));
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder()
                .withFacility(new ArrayList<>(List.of(facilityWithClientReferenceId("crid-1")))).withRequestInfo().build();

        List<Facility> result = facilityService.create(request, false);

        assertEquals(1, result.size());
        verify(repository, times(1)).save(anyList(), eq("create-facility-topic"));
    }

    @Test
    @DisplayName("should not call kafka topic if no valid facility found for update")
    void shouldNotCallKafkaTopicUpdate() {
        FacilityBulkRequest facilityBulkRequest = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();
        facilityBulkRequest.getFacilities().get(0).setHasErrors(true);

        List<Facility> facility = facilityService.update(facilityBulkRequest, false);

        assertEquals(0, facility.size());
        verify(repository, times(0)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should call kafka topic if valid facility found for update")
    void shouldCallKafkaTopicUpdate() {
        FacilityBulkRequest facilityBulkRequest = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();

        List<Facility> facility = facilityService.update(facilityBulkRequest, false);

        assertEquals(1, facility.size());
        verify(repository, times(1)).save(anyList(), eq("update-facility-topic"));
    }

    @Test
    @DisplayName("should not call kafka topic if no valid facility found for delete")
    void shouldNotCallKafkaTopicDelete() {
        FacilityBulkRequest facilityBulkRequest = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();
        facilityBulkRequest.getFacilities().get(0).setHasErrors(true);

        List<Facility> facility = facilityService.delete(facilityBulkRequest, false);

        assertEquals(0, facility.size());
        verify(repository, times(0)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should call kafka topic if valid facility found for delete")
    void shouldCallKafkaTopicDelete() {
        FacilityBulkRequest facilityBulkRequest = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();

        List<Facility> facility = facilityService.delete(facilityBulkRequest, false);

        assertEquals(1, facility.size());
        verify(repository, times(1)).save(anyList(), eq("delete-facility-topic"));
    }
}
