package org.digit.health.sync.context.step;

import org.digit.health.sync.context.enums.RecordIdType;
import org.digit.health.sync.context.enums.StepSyncStatus;
import org.digit.health.sync.context.enums.SyncErrorCode;
import org.digit.health.sync.context.metric.SyncStepMetric;
import org.digit.health.sync.helper.DeliveryRequestTestBuilder;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.digit.health.sync.utils.Properties;
import org.digit.health.sync.web.models.request.DeliveryRequest;
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
class DeliverySyncStepTest {
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
        when(properties.getDeliveryBaseUrl()).thenReturn("some-url");
        when(properties.getDeliveryCreateEndpoint()).thenReturn("some-endpoint");
    }

    @Test
    @DisplayName("delivery sync step should call delivery service")
    void testThatDeliverySyncStepShouldCallDeliveryService() {
        SyncStep deliverySyncStep = new DeliverySyncStep(applicationContext);

        deliverySyncStep.handle(DeliveryRequestTestBuilder
                .builder()
                .withDummyClientReferenceId()
                .build());

        verify(serviceRequestRepository, times(1))
                .fetchResult(any(StringBuilder.class),
                        any(DeliveryRequest.class),
                        eq(ResponseEntity.class));
    }

    @Test
    @DisplayName("delivery sync step should publish success metric on successful execution")
    void testThatDeliverySyncStepShouldPublishSuccessMetricOnSuccessfulExecution() {
        SyncStep deliverySyncStep = Mockito.spy(new DeliverySyncStep(applicationContext));
        DeliveryRequest deliveryRequest = DeliveryRequestTestBuilder
                .builder()
                .withDummyClientReferenceId()
                .build();
        SyncStepMetric syncStepMetric = SyncStepMetric.builder()
                .status(StepSyncStatus.COMPLETED)
                .recordId(deliveryRequest.getClientReferenceId())
                .recordIdType(RecordIdType.DELIVERY)
                .build();

        deliverySyncStep.handle(deliveryRequest);

        verify(deliverySyncStep, times(1))
                .notifyObservers(syncStepMetric);
    }

    @Test
    @DisplayName("delivery sync step should not throw custom exception in case of any error")
    void testThatDeliverySyncStepThrowsCustomExceptionInCaseOfAnyError() {
        SyncStep deliverySyncStep = new DeliverySyncStep(applicationContext);
        when(serviceRequestRepository.fetchResult(any(StringBuilder.class),
                any(DeliveryRequest.class),
                eq(ResponseEntity.class))).thenThrow(CustomException.class);
        DeliveryRequest deliveryRequest = DeliveryRequestTestBuilder
                .builder()
                .withDummyClientReferenceId()
                .build();

        assertDoesNotThrow(() -> deliverySyncStep
                .handle(deliveryRequest));
    }

    @Test
    @DisplayName("delivery sync step should publish failure metric in case of any error")
    void testThatDeliverySyncStepShouldPublishFailureMetricInCaseOfError() {
        String errorMessage = "some_message";
        SyncStep deliverySyncStep = Mockito.spy(new DeliverySyncStep(applicationContext));
        DeliveryRequest deliveryRequest = DeliveryRequestTestBuilder
                .builder()
                .withDummyClientReferenceId()
                .build();
        SyncStepMetric syncStepMetric = SyncStepMetric.builder()
                .status(StepSyncStatus.FAILED)
                .recordId(deliveryRequest.getClientReferenceId())
                .recordIdType(RecordIdType.DELIVERY)
                .errorCode(SyncErrorCode.ERROR_IN_REST_CALL.name())
                .errorMessage(SyncErrorCode.ERROR_IN_REST_CALL.message(errorMessage))
                .build();
        when(serviceRequestRepository.fetchResult(any(StringBuilder.class),
                any(DeliveryRequest.class),
                eq(ResponseEntity.class))).thenThrow(new CustomException("some_code", errorMessage));

        deliverySyncStep.handle(deliveryRequest);

        verify(deliverySyncStep, times(1))
                .notifyObservers(syncStepMetric);
    }
}
