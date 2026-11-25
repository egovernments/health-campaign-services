package org.egov.individual.service;

import org.egov.common.error.handler.ErrorHandler;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.UserDetails;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.util.BeneficiaryIdGenUtil;
import org.egov.individual.util.ErrorRecordTest;
import org.egov.tracer.ExceptionAdvise;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Bulk messages from Kafka: an encryption failure must not lose the records (the consumer parks the
 * message), and create encrypts before it makes the user account, so a replay can make it.
 */
class IndividualServiceQueueTest {

    private static final String SAVE_TOPIC = "save-individual-topic";

    private final IndividualProperties properties = mock(IndividualProperties.class);
    private final IndividualRepository repository = mock(IndividualRepository.class);
    private final IndividualEncryptionService encryptionService = mock(IndividualEncryptionService.class);
    private final UserIntegrationService userIntegrationService = mock(UserIntegrationService.class);
    private final ExceptionAdvise exceptionAdvise = mock(ExceptionAdvise.class);
    private ExceptionAdvise previousAdvise;
    private IndividualService service;

    @BeforeEach
    void setUp() {
        previousAdvise = ErrorHandler.exceptionAdviseInstance;
        ErrorHandler.exceptionAdviseInstance = exceptionAdvise;
        when(properties.getSaveIndividualTopic()).thenReturn(SAVE_TOPIC);
        when(properties.isUserSyncEnabled()).thenReturn(true);
        service = new IndividualService(repository, Collections.emptyList(), properties,
                mock(EnrichmentService.class), encryptionService, userIntegrationService,
                mock(NotificationService.class), mock(BeneficiaryIdGenUtil.class));
    }

    @AfterEach
    void tearDown() {
        ErrorHandler.exceptionAdviseInstance = previousAdvise;
    }

    private static IndividualBulkRequest request(Individual... individuals) {
        return IndividualBulkRequest.builder().requestInfo(ErrorRecordTest.requestInfo())
                .individuals(new ArrayList<>(List.of(individuals))).build();
    }

    private static Individual systemUser(String clientReferenceId, String username) {
        Individual individual = ErrorRecordTest.individual();
        individual.setClientReferenceId(clientReferenceId);
        individual.setIsSystemUser(true);
        individual.setUserDetails(UserDetails.builder().username(username).build());
        return individual;
    }

    private static Individual storedCopy(Individual source) {
        return Individual.builder().id(source.getId()).clientReferenceId(source.getClientReferenceId())
                .tenantId(source.getTenantId()).mobileNumber("1|cipher-of-mobile").build();
    }

    private static CustomException encFailure() {
        return new CustomException("ENCRYPTION_SERVICE_ERROR", "Error occurred while calling Encryption Service");
    }

    // ---------------------------------------------------------------- queue: encryption failure is thrown

    @Test
    @DisplayName("queue create: enc-service fails -> thrown for the consumer to park; nothing to the error topic, no user account made")
    void queueCreateThrowsEncFailure() {
        when(encryptionService.encrypt(any(), anyList(), anyString(), anyBoolean())).thenThrow(encFailure());

        CustomException e = assertThrows(CustomException.class,
                () -> service.createFromQueue(request(systemUser("cref-1", "user-0001"))));

        assertEquals("ENCRYPTION_SERVICE_ERROR", e.getCode());
        verifyNoInteractions(exceptionAdvise);
        verifyNoInteractions(userIntegrationService);
        verify(repository, never()).save(anyList(), anyString());
    }

    @Test
    @DisplayName("API bulk create (synchronous=true): enc-service fails -> reported as an error record as before, not thrown")
    void apiBulkCreateStillReportsEncFailure() {
        when(encryptionService.encrypt(any(), anyList(), anyString(), anyBoolean())).thenThrow(encFailure());

        service.create(request(systemUser("cref-1", "user-0001")), true);

        verify(exceptionAdvise).exceptionHandler(anyList());
        verifyNoInteractions(userIntegrationService);
    }

    @Test
    @DisplayName("queue update: enc-service fails -> thrown for the consumer to park; nothing to the error topic")
    void queueUpdateThrowsEncFailure() {
        when(encryptionService.encrypt(any(), anyList(), anyString(), anyBoolean())).thenThrow(encFailure());

        assertThrows(CustomException.class, () -> service.updateFromQueue(request(ErrorRecordTest.individual())));

        verifyNoInteractions(exceptionAdvise);
        verify(repository, never()).save(anyList(), anyString());
    }

    @Test
    @DisplayName("queue delete: enc-service fails -> thrown for the consumer to park; nothing to the error topic")
    void queueDeleteThrowsEncFailure() {
        when(encryptionService.toStoredForm(anyList())).thenThrow(encFailure());

        assertThrows(CustomException.class, () -> service.deleteFromQueue(request(ErrorRecordTest.individual())));

        verifyNoInteractions(exceptionAdvise);
        verify(repository, never()).save(anyList(), anyString());
    }

    @Test
    @DisplayName("queue create: a failure that is not encryption is still reported as an error record, not thrown")
    void queueCreateOtherFailureStillReported() {
        Individual individual = ErrorRecordTest.individual();
        when(encryptionService.encrypt(any(), anyList(), anyString(), anyBoolean()))
                .thenReturn(new ArrayList<>(List.of(storedCopy(individual))));
        org.mockito.Mockito.doThrow(new CustomException("EVENT_BUS_FAILURE", "send failed"))
                .when(repository).save(anyList(), anyString());

        service.createFromQueue(request(individual));

        verify(exceptionAdvise).exceptionHandler(anyList());
    }

    // ---------------------------------------------------------------- create: encrypt first, then the user account

    @Test
    @DisplayName("create encrypts before the user service call; the saved stored copy gets the new userId and userUuid")
    @SuppressWarnings("unchecked")
    void createEncryptsBeforeUserAccount() {
        Individual individual = systemUser("cref-1", "user-0001");
        Individual stored = storedCopy(individual);
        when(encryptionService.encrypt(any(), anyList(), anyString(), anyBoolean()))
                .thenReturn(new ArrayList<>(List.of(stored)));
        when(userIntegrationService.createUsersBulk(anyList(), any(), anyBoolean()))
                .thenReturn(new UserIntegrationService.BulkUserResult(
                        List.of(Map.of("username", "user-0001", "id", 42L, "uuid", "user-uuid-1")),
                        Collections.emptyList()));

        service.createFromQueue(request(individual));

        InOrder order = inOrder(encryptionService, userIntegrationService, repository);
        order.verify(encryptionService).encrypt(any(), anyList(), anyString(), anyBoolean());
        order.verify(userIntegrationService).createUsersBulk(anyList(), any(), anyBoolean());
        ArgumentCaptor<List<Individual>> saved = ArgumentCaptor.forClass(List.class);
        order.verify(repository).save(saved.capture(), eq(SAVE_TOPIC));
        assertEquals(1, saved.getValue().size());
        assertSame(stored, saved.getValue().get(0));
        assertEquals("42", stored.getUserId());
        assertEquals("user-uuid-1", stored.getUserUuid());
    }

    @Test
    @DisplayName("create: a record the user service rejects is not saved; the other record's stored copy is")
    @SuppressWarnings("unchecked")
    void createSavesOnlyRecordsWithUserAccount() {
        Individual ok = systemUser("cref-1", "user-0001");
        Individual rejected = systemUser("cref-2", "user-0002");
        rejected.setId("ind-2");
        Individual storedOk = storedCopy(ok);
        Individual storedRejected = storedCopy(rejected);
        when(encryptionService.encrypt(any(), anyList(), anyString(), anyBoolean()))
                .thenReturn(new ArrayList<>(List.of(storedOk, storedRejected)));
        when(userIntegrationService.createUsersBulk(anyList(), any(), anyBoolean()))
                .thenReturn(new UserIntegrationService.BulkUserResult(
                        List.of(Map.of("username", "user-0001", "id", 42L, "uuid", "user-uuid-1")),
                        List.of(Map.of("username", "user-0002", "code", "DuplicateUserNameException", "message", "duplicate"))));

        service.createFromQueue(request(ok, rejected));

        ArgumentCaptor<List<Individual>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).save(saved.capture(), eq(SAVE_TOPIC));
        assertEquals(List.of(storedOk), saved.getValue());
        verify(exceptionAdvise).exceptionHandler(anyList()); // the rejected one is reported
    }

    // ---------------------------------------------------------------- delete: encrypt first, then the user account

    @Test
    @DisplayName("delete: enc-service fails -> the user account is not deactivated")
    void deleteEncFailureLeavesUserActive() {
        when(encryptionService.toStoredForm(anyList())).thenThrow(encFailure());

        assertThrows(CustomException.class, () -> service.deleteFromQueue(request(systemUser("cref-1", "user-0001"))));

        verify(userIntegrationService, never()).deleteUser(anyList(), any());
        verify(repository, never()).save(anyList(), anyString());
    }

    @Test
    @DisplayName("delete encrypts before the user service call; a record the user service fails for is not sent")
    @SuppressWarnings("unchecked")
    void deleteEncryptsBeforeUserAccount() {
        when(properties.getDeleteIndividualTopic()).thenReturn("delete-individual-topic");
        Individual ok = systemUser("cref-1", "user-0001");
        Individual failed = systemUser("cref-2", "user-0002");
        failed.setId("ind-2");
        Individual storedOk = storedCopy(ok);
        when(encryptionService.toStoredForm(anyList()))
                .thenReturn(new ArrayList<>(List.of(storedOk, storedCopy(failed))));
        when(userIntegrationService.deleteUser(anyList(), any())).thenAnswer(call -> {
            if (((List<Individual>) call.getArgument(0)).get(0) == failed) {
                throw new RuntimeException("user service down");
            }
            return Collections.emptyList();
        });

        service.deleteFromQueue(request(ok, failed));

        InOrder order = inOrder(encryptionService, userIntegrationService, repository);
        order.verify(encryptionService).toStoredForm(anyList());
        order.verify(userIntegrationService, org.mockito.Mockito.times(2)).deleteUser(anyList(), any());
        ArgumentCaptor<List<Individual>> saved = ArgumentCaptor.forClass(List.class);
        order.verify(repository).save(saved.capture(), eq("delete-individual-topic"));
        assertEquals(1, saved.getValue().size());
        assertSame(storedOk, saved.getValue().get(0));
    }

    @Test
    @DisplayName("queue create does not decrypt a reply (the consumer does not use it); the API create still does")
    void queueCreateDoesNotDecrypt() {
        Individual individual = ErrorRecordTest.individual();
        when(encryptionService.encrypt(any(), anyList(), anyString(), anyBoolean()))
                .thenReturn(new ArrayList<>(List.of(storedCopy(individual))));

        service.createFromQueue(request(individual));
        verify(encryptionService, never()).decrypt(anyList(), anyString(), any());

        service.create(request(ErrorRecordTest.individual()), true);
        verify(encryptionService).decrypt(anyList(), anyString(), any());
    }
}
