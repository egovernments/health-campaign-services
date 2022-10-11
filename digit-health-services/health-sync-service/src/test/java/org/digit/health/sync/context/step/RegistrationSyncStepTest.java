package org.digit.health.sync.context.step;

import org.digit.health.sync.context.enums.RecordIdType;
import org.digit.health.sync.context.enums.StepSyncStatus;
import org.digit.health.sync.context.enums.SyncErrorCode;
import org.digit.health.sync.context.metric.SyncStepMetric;
import org.digit.health.sync.helper.HouseholdRegistrationRequestTestBuilder;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.digit.health.sync.utils.Properties;
import org.digit.health.sync.web.models.request.HouseholdRegistrationRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationSyncStepTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ServiceRequestRepository serviceRequestRepository;

    @Mock
    private Properties properties;

    @BeforeEach
    void setUp() {
        when(applicationContext.getBean(ServiceRequestRepository.class))
                .thenReturn(serviceRequestRepository);
        when(applicationContext.getBean(Properties.class)).thenReturn(properties);
        when(properties.getRegistrationBaseUrl()).thenReturn("some-url");
        when(properties.getRegistrationCreateEndpoint()).thenReturn("some-endpoint");
    }

    @Test
    @DisplayName("registration sync step should call registration service")
    void testThatRegistrationSyncStepShouldCallRegistrationService() {
        SyncStep registrationSyncStep = new RegistrationSyncStep(applicationContext);

        registrationSyncStep.handle(HouseholdRegistrationRequestTestBuilder
                .builder()
                .withDummyClientReferenceId()
                .build());

        verify(serviceRequestRepository, times(1))
                .fetchResult(any(StringBuilder.class),
                any(HouseholdRegistrationRequest.class),
                eq(ResponseEntity.class));
    }

    @Test
    @DisplayName("registration sync step should publish success metric on successful execution")
    void testThatRegistrationSyncStepShouldPublishSuccessMetricOnSuccessfulExecution() {
        SyncStep registrationSyncStep = Mockito.spy(new RegistrationSyncStep(applicationContext));
        HouseholdRegistrationRequest householdRegistrationRequest = HouseholdRegistrationRequestTestBuilder
                .builder()
                .withDummyClientReferenceId()
                .build();
        SyncStepMetric syncStepMetric = SyncStepMetric.builder()
                .status(StepSyncStatus.COMPLETED)
                .recordId(householdRegistrationRequest.getClientReferenceId())
                .recordIdType(RecordIdType.REGISTRATION)
                .build();

        registrationSyncStep.handle(householdRegistrationRequest);

        verify(registrationSyncStep, times(1))
                .notifyObservers(syncStepMetric);
    }

    @Test
    @DisplayName("registration sync step should not throw custom exception in case of any error")
    void testThatRegistrationSyncStepThrowsCustomExceptionInCaseOfAnyError() {
        SyncStep registrationSyncStep = new RegistrationSyncStep(applicationContext);
        when(serviceRequestRepository.fetchResult(any(StringBuilder.class),
                any(HouseholdRegistrationRequest.class),
                eq(ResponseEntity.class))).thenThrow(CustomException.class);
        HouseholdRegistrationRequest householdRegistrationRequest = HouseholdRegistrationRequestTestBuilder
                .builder()
                .withDummyClientReferenceId()
                .build();

        assertDoesNotThrow(() -> registrationSyncStep
                .handle(householdRegistrationRequest));
    }

    @Test
    @DisplayName("registration sync step should publish failure metric in case of any error")
    void testThatRegistrationSyncStepShouldPublishFailureMetricInCaseOfError() {
        String errorMessage = "some_message";
        SyncStep registrationSyncStep = Mockito.spy(new RegistrationSyncStep(applicationContext));
        HouseholdRegistrationRequest householdRegistrationRequest = HouseholdRegistrationRequestTestBuilder
                .builder()
                .withDummyClientReferenceId()
                .build();
        SyncStepMetric syncStepMetric = SyncStepMetric.builder()
                .status(StepSyncStatus.FAILED)
                .recordId(householdRegistrationRequest.getClientReferenceId())
                .recordIdType(RecordIdType.REGISTRATION)
                .errorCode(SyncErrorCode.ERROR_IN_REST_CALL.name())
                .errorMessage(SyncErrorCode.ERROR_IN_REST_CALL.message(errorMessage))
                .build();
        when(serviceRequestRepository.fetchResult(any(StringBuilder.class),
                any(HouseholdRegistrationRequest.class),
                eq(ResponseEntity.class))).thenThrow(new CustomException("some_code", errorMessage));

        registrationSyncStep.handle(householdRegistrationRequest);

        verify(registrationSyncStep, times(1))
                .notifyObservers(syncStepMetric);
    }
}
