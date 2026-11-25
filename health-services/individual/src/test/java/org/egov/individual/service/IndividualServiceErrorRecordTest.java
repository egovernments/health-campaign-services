package org.egov.individual.service;

import org.egov.common.error.handler.ErrorHandler;
import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.AddressType;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.UserDetails;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.util.BeneficiaryIdGenUtil;
import org.egov.individual.util.ErrorRecordTest;
import org.egov.individual.validators.AddressTypeValidator;
import org.egov.tracer.ExceptionAdvise;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ErrorDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What leaves the service when a record fails. Bulk: the list handed to the tracer (error topic,
 * ES error index). Non-bulk: the exception message (the tracer writes it to the pod log).
 */
class IndividualServiceErrorRecordTest {

    private static final String USERNAME = "test-username-0001";

    private final IndividualProperties properties = mock(IndividualProperties.class);
    private final IndividualEncryptionService encryptionService = mock(IndividualEncryptionService.class);
    private final UserIntegrationService userIntegrationService = mock(UserIntegrationService.class);
    private final ExceptionAdvise exceptionAdvise = mock(ExceptionAdvise.class);
    private ExceptionAdvise previousAdvise;

    @BeforeEach
    void setUp() {
        previousAdvise = ErrorHandler.exceptionAdviseInstance;
        ErrorHandler.exceptionAdviseInstance = exceptionAdvise;
    }

    @AfterEach
    void tearDown() {
        ErrorHandler.exceptionAdviseInstance = previousAdvise;
    }

    private IndividualService service(List<Validator<IndividualBulkRequest, Individual>> validators) {
        return new IndividualService(mock(IndividualRepository.class), validators, properties,
                mock(EnrichmentService.class), encryptionService, userIntegrationService,
                mock(NotificationService.class), mock(BeneficiaryIdGenUtil.class));
    }

    private static IndividualBulkRequest request(Individual individual) {
        return IndividualBulkRequest.builder().requestInfo(ErrorRecordTest.requestInfo())
                .individuals(new ArrayList<>(List.of(individual))).build();
    }

    private static List<String> secrets() {
        List<String> secrets = new ArrayList<>(ErrorRecordTest.SECRETS);
        secrets.add(USERNAME);
        return secrets;
    }

    @SuppressWarnings("unchecked")
    private String sentToErrorTopic() {
        ArgumentCaptor<List<ErrorDetail>> captor = ArgumentCaptor.forClass(List.class);
        verify(exceptionAdvise).exceptionHandler(captor.capture());
        assertEquals(1, captor.getValue().size());
        ErrorDetail detail = captor.getValue().get(0);
        StringBuilder all = new StringBuilder(detail.getApiDetails().getRequestBody());
        detail.getErrors().forEach(e -> all.append(' ').append(e.getErrorCode()).append(' ')
                .append(e.getErrorMessage()).append(' ').append(e.getException()));
        return all.toString();
    }

    @Test
    void bulkEncryptFailureSendsIdsOnly() {
        when(encryptionService.encrypt(any(), anyList(), anyString(), anyBoolean()))
                .thenThrow(new CustomException("ENCRYPTION_SERVICE_ERROR", "Error occurred while calling Encryption Service"));

        service(Collections.emptyList()).create(request(ErrorRecordTest.individual()), true);

        String sent = sentToErrorTopic();
        secrets().forEach(s -> assertFalse(sent.contains(s), "leaked to the error topic: " + s));
        assertTrue(sent.contains("cref-1"));
        assertTrue(sent.contains("ENCRYPTION_SERVICE_ERROR"));
    }

    @Test
    void nonBulkEncryptFailureMessageHasNoPii() {
        when(encryptionService.encrypt(any(), anyList(), anyString(), anyBoolean()))
                .thenThrow(new CustomException("ENCRYPTION_SERVICE_ERROR", "Error occurred while calling Encryption Service"));

        CustomException e = assertThrows(CustomException.class,
                () -> service(Collections.emptyList()).create(request(ErrorRecordTest.individual()), false));

        secrets().forEach(s -> assertFalse(e.getMessage().contains(s), "leaked in the exception message: " + s));
        assertTrue(e.getMessage().contains("cref-1"));
        assertEquals("ENCRYPTION_SERVICE_ERROR", e.getCode());
    }

    @Test
    void nonBulkValidationFailureMessageHasNoPii() {
        Individual individual = ErrorRecordTest.individual();
        individual.setAddress(new ArrayList<>(List.of(
                Address.builder().type(AddressType.PERMANENT).city("test-city").tenantId("test-tenant").build(),
                Address.builder().type(AddressType.PERMANENT).city("test-city").tenantId("test-tenant").build())));

        CustomException e = assertThrows(CustomException.class,
                () -> service(List.of(new AddressTypeValidator())).create(request(individual), false));

        secrets().forEach(s -> assertFalse(e.getMessage().contains(s), "leaked in the exception message: " + s));
        assertTrue(e.getMessage().contains("cref-1"));
    }

    @Test
    void bulkUserServiceRejectionHasNoUsernameOrMobile() {
        Individual individual = ErrorRecordTest.individual();
        individual.setIsSystemUser(true);
        individual.setUserDetails(UserDetails.builder().username(USERNAME).build());
        when(properties.isUserSyncEnabled()).thenReturn(true);
        // encryption now runs before the user service call and returns one stored copy per record
        when(encryptionService.encrypt(any(), anyList(), anyString(), anyBoolean()))
                .thenReturn(new ArrayList<>(List.of(Individual.builder().id("ind-1").clientReferenceId("cref-1").build())));
        when(userIntegrationService.createUsersBulk(anyList(), any(), anyBoolean()))
                .thenReturn(new UserIntegrationService.BulkUserResult(Collections.emptyList(),
                        List.of(Map.of("username", USERNAME, "code", "DuplicateUserNameException",
                                "message", "duplicate user"))));

        service(Collections.emptyList()).create(request(individual), true);

        String sent = sentToErrorTopic();
        secrets().forEach(s -> assertFalse(sent.contains(s), "leaked to the error topic: " + s));
        assertTrue(sent.contains("INDIVIDUAL_BULK_CREATE_DuplicateUserNameException"));
        assertTrue(sent.contains("cref-1"));
    }
}
