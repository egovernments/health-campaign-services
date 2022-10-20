package org.digit.health.sync.context.step;

import org.digit.health.sync.context.enums.RecordIdType;
import org.digit.health.sync.context.enums.StepSyncStatus;
import org.digit.health.sync.context.enums.SyncErrorCode;
import org.digit.health.sync.context.metric.SyncStepMetric;
import org.digit.health.sync.helper.HouseholdRegistrationRequestTestBuilder;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.digit.health.sync.utils.Properties;
import org.digit.health.sync.web.models.request.HouseholdRegistrationRequest;
import org.digit.health.sync.web.models.response.RegistrationResponse;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                any(List.class),
                eq(RegistrationResponse.class));
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
                .recordId(householdRegistrationRequest.getHousehold().getClientReferenceId())
                .recordIdType(RecordIdType.REGISTRATION)
                .build();

        registrationSyncStep.handle(householdRegistrationRequest);

        verify(registrationSyncStep, times(1))
                .notifyObservers(syncStepMetric);
    }

    @Test
    @DisplayName("registration sync step should publish metrics and throw custom exception in case of any error")
    void testThatRegistrationSyncStepThrowsCustomExceptionInCaseOfAnyError() {
        String errorMessage = "some_message";
        SyncStep registrationSyncStep = Mockito.spy(new RegistrationSyncStep(applicationContext));
        when(serviceRequestRepository.fetchResult(any(StringBuilder.class),
                any(List.class),
                eq(RegistrationResponse.class))).thenThrow(new CustomException("some_code", errorMessage));
        HouseholdRegistrationRequest householdRegistrationRequest = HouseholdRegistrationRequestTestBuilder
                .builder()
                .withDummyClientReferenceId()
                .build();
        SyncStepMetric syncStepMetric = SyncStepMetric.builder()
                .status(StepSyncStatus.FAILED)
                .recordId(householdRegistrationRequest.getHousehold().getClientReferenceId())
                .recordIdType(RecordIdType.REGISTRATION)
                .errorCode(SyncErrorCode.ERROR_IN_REST_CALL.name())
                .errorMessage(SyncErrorCode.ERROR_IN_REST_CALL.message(errorMessage))
                .build();

        CustomException customException = null;

        try {
            registrationSyncStep
                    .handle(householdRegistrationRequest);
        } catch (CustomException ex) {
            customException = ex;
        }

        assertNotNull(customException);

        verify(registrationSyncStep, times(1))
                .notifyObservers(syncStepMetric);
    }

    @Test
    @DisplayName("registration sync step should publish failure metric and throw exception in case of any error")
    void testThatRegistrationSyncStepShouldPublishFailureMetricAndThrowExceptionInCaseOfError() {
        String errorMessage = "some_message";
        SyncStep registrationSyncStep = Mockito.spy(new RegistrationSyncStep(applicationContext));
        HouseholdRegistrationRequest householdRegistrationRequest = HouseholdRegistrationRequestTestBuilder
                .builder()
                .withDummyClientReferenceId()
                .build();
        SyncStepMetric syncStepMetric = SyncStepMetric.builder()
                .status(StepSyncStatus.FAILED)
                .recordId(householdRegistrationRequest.getHousehold().getClientReferenceId())
                .recordIdType(RecordIdType.REGISTRATION)
                .errorCode(SyncErrorCode.ERROR_IN_REST_CALL.name())
                .errorMessage(SyncErrorCode.ERROR_IN_REST_CALL.message(errorMessage))
                .build();
        when(serviceRequestRepository.fetchResult(any(StringBuilder.class),
                any(List.class),
                eq(RegistrationResponse.class)))
                .thenThrow(new CustomException("some_code", errorMessage));
        Exception ex = null;
        try {
            registrationSyncStep.handle(householdRegistrationRequest);
        } catch (Exception exception) {
            ex = exception;
        }

        assertNotNull(ex);

        verify(registrationSyncStep, times(1))
                .notifyObservers(syncStepMetric);
    }
}
