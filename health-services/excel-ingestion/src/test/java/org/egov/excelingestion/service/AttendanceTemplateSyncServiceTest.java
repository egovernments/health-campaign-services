package org.egov.excelingestion.service;

import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.repository.GeneratedFileRepository;
import org.egov.excelingestion.web.models.AttendanceSyncEvent;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerationSearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Expiry of generated attendance templates when the attendance service changes underneath them. */
@ExtendWith(MockitoExtension.class)
class AttendanceTemplateSyncServiceTest {

    private static final String TENANT = "mz";
    private static final String OTHER_TENANT = "ba";
    private static final String REGISTER_ID = "register-uuid-1";

    @Mock private GeneratedFileRepository generatedFileRepository;
    @Mock private GenerationService generationService;

    private AttendanceTemplateSyncService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceTemplateSyncService(generatedFileRepository, generationService);
    }

    @Test
    void registerDelete_expiresTheRegistersLiveTemplates() throws Exception {
        GenerateResource live = record("gen-1");
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
                .thenReturn(List.of(live))
                .thenReturn(Collections.emptyList());

        service.handleRegisterDeletes(List.of(deletedRegister(REGISTER_ID, TENANT)));

        ArgumentCaptor<List<GenerateResource>> captor = ArgumentCaptor.forClass(List.class);
        verify(generationService).expireRecords(eq(TENANT), captor.capture(), anyString());
        assertEquals(List.of("gen-1"), captor.getValue().stream().map(GenerateResource::getId).toList());
    }

    @Test
    void registerDelete_searchesBothReferenceAndAdditionalDetailsShapes() throws Exception {
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
                .thenReturn(Collections.emptyList());

        service.handleRegisterDeletes(List.of(deletedRegister(REGISTER_ID, TENANT)));

        ArgumentCaptor<GenerationSearchCriteria> captor = ArgumentCaptor.forClass(GenerationSearchCriteria.class);
        verify(generatedFileRepository, times(2)).search(captor.capture());

        GenerationSearchCriteria byReference = captor.getAllValues().get(0);
        assertEquals(List.of(REGISTER_ID), byReference.getReferenceIds());
        assertEquals(List.of(ProcessingConstants.REFERENCE_TYPE_ATTENDANCE_REGISTER), byReference.getReferenceTypes());
        assertEquals(GenerationConstants.LIVE_STATUSES, byReference.getStatuses());

        GenerationSearchCriteria byAdditionalDetails = captor.getAllValues().get(1);
        assertEquals(Map.of(ProcessingConstants.ADDITIONAL_DETAILS_REGISTER_ID, REGISTER_ID),
                byAdditionalDetails.getAdditionalDetails());
        assertTrue(byAdditionalDetails.getTypes()
                .contains(ProcessingConstants.ATTENDANCE_REGISTER_ATTENDEE_TYPE));
    }

    @Test
    void registerDelete_dedupesRecordsMatchedByBothShapes() throws Exception {
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
                .thenReturn(List.of(record("gen-1")))
                .thenReturn(List.of(record("gen-1")));

        service.handleRegisterDeletes(List.of(deletedRegister(REGISTER_ID, TENANT)));

        ArgumentCaptor<List<GenerateResource>> captor = ArgumentCaptor.forClass(List.class);
        verify(generationService).expireRecords(eq(TENANT), captor.capture(), anyString());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    void registerDelete_withNoLiveTemplate_expiresNothing() throws Exception {
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
                .thenReturn(Collections.emptyList());

        service.handleRegisterDeletes(List.of(deletedRegister(REGISTER_ID, TENANT)));

        verify(generationService, never()).expireRecords(anyString(), any(), anyString());
    }

    @Test
    void registerDelete_withEntryMissingTenant_isSkipped() throws Exception {
        service.handleRegisterDeletes(List.of(deletedRegister(REGISTER_ID, "  ")));

        verify(generatedFileRepository, never()).search(any());
        verify(generationService, never()).expireRecords(anyString(), any(), anyString());
    }

    @Test
    void registerDelete_withNoRegisters_isSkipped() throws Exception {
        service.handleRegisterDeletes(Collections.emptyList());

        verify(generatedFileRepository, never()).search(any());
    }

    @Test
    void deEnrolment_expiresTheRegistersTemplate() throws Exception {
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
                .thenReturn(List.of(record("gen-1")))
                .thenReturn(Collections.emptyList());

        service.handleDeEnrolments(List.of(enrolment(REGISTER_ID, TENANT, 1755000000000L)), "attendee");

        verify(generationService).expireRecords(eq(TENANT), any(), anyString());
    }

    @Test
    void deEnrolment_withoutDate_isIgnoredSoTagEditsDoNotExpire() throws Exception {
        service.handleDeEnrolments(List.of(enrolment(REGISTER_ID, TENANT, null)), "attendee");

        verify(generatedFileRepository, never()).search(any());
        verify(generationService, never()).expireRecords(anyString(), any(), anyString());
    }

    @Test
    void deEnrolment_withZeroDate_isIgnored() throws Exception {
        service.handleDeEnrolments(List.of(enrolment(REGISTER_ID, TENANT, 0L)), "staff");

        verify(generatedFileRepository, never()).search(any());
    }

    @Test
    void deEnrolments_forOneRegisterAreSearchedOnce() throws Exception {
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
                .thenReturn(Collections.emptyList());

        service.handleDeEnrolments(List.of(
                enrolment(REGISTER_ID, TENANT, 1755000000000L),
                enrolment(REGISTER_ID, TENANT, 1755000000000L)), "attendee");

        // Two searches (one per shape) for the single distinct register, not per entry
        verify(generatedFileRepository, times(2)).search(any());
    }

    @Test
    void deEnrolments_acrossTenants_expireIndependently() throws Exception {
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
                .thenReturn(List.of(record("gen-1")))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(record("gen-2", OTHER_TENANT)))
                .thenReturn(Collections.emptyList());

        service.handleDeEnrolments(List.of(
                enrolment(REGISTER_ID, TENANT, 1755000000000L),
                enrolment("register-uuid-2", OTHER_TENANT, 1755000000000L)), "attendee");

        // Each tenant must get only its own records — never the other tenant's
        ArgumentCaptor<List<GenerateResource>> firstTenantRecords = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<GenerateResource>> otherTenantRecords = ArgumentCaptor.forClass(List.class);
        verify(generationService).expireRecords(eq(TENANT), firstTenantRecords.capture(), anyString());
        verify(generationService).expireRecords(eq(OTHER_TENANT), otherTenantRecords.capture(), anyString());
        assertEquals(List.of("gen-1"),
                firstTenantRecords.getValue().stream().map(GenerateResource::getId).toList());
        assertEquals(List.of("gen-2"),
                otherTenantRecords.getValue().stream().map(GenerateResource::getId).toList());
    }

    @Test
    void searchFailureForOneTenant_expiresTheOtherThenRethrows() throws Exception {
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
                .thenThrow(new RuntimeException("db down"))
                .thenReturn(List.of(record("gen-2", OTHER_TENANT)))
                .thenReturn(Collections.emptyList());

        // Rethrown so the listener does not commit an event whose template is still being served
        assertThrows(IllegalStateException.class, () -> service.handleDeEnrolments(List.of(
                enrolment(REGISTER_ID, TENANT, 1755000000000L),
                enrolment("register-uuid-2", OTHER_TENANT, 1755000000000L)), "attendee"));

        verify(generationService, never()).expireRecords(eq(TENANT), any(), anyString());
        verify(generationService).expireRecords(eq(OTHER_TENANT), any(), anyString());
    }

    @Test
    void expiryFailure_isRethrownSoTheEventIsRetried() throws Exception {
        when(generatedFileRepository.search(any(GenerationSearchCriteria.class)))
                .thenReturn(List.of(record("gen-1")))
                .thenReturn(Collections.emptyList());
        when(generationService.expireRecords(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("kafka down"));

        assertThrows(IllegalStateException.class,
                () -> service.handleRegisterDeletes(List.of(deletedRegister(REGISTER_ID, TENANT))));
    }

    private AttendanceSyncEvent.DeletedRegister deletedRegister(String id, String tenantId) {
        return AttendanceSyncEvent.DeletedRegister.builder().id(id).tenantId(tenantId).build();
    }

    private AttendanceSyncEvent.Enrolment enrolment(String registerId, String tenantId, Long denrollmentDate) {
        return AttendanceSyncEvent.Enrolment.builder()
                .registerId(registerId).tenantId(tenantId).denrollmentDate(denrollmentDate).build();
    }

    private GenerateResource record(String id) {
        return record(id, TENANT);
    }

    private GenerateResource record(String id, String tenantId) {
        return GenerateResource.builder()
                .id(id)
                .tenantId(tenantId)
                .referenceId(REGISTER_ID)
                .referenceType(ProcessingConstants.REFERENCE_TYPE_ATTENDANCE_REGISTER)
                .type(ProcessingConstants.ATTENDANCE_REGISTER_ATTENDEE_TYPE)
                .status(GenerationConstants.STATUS_COMPLETED)
                .build();
    }
}
