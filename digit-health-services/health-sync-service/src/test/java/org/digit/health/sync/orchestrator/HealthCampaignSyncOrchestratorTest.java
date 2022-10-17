package org.digit.health.sync.orchestrator;

import org.digit.health.sync.context.HealthCampaignSyncContext;
import org.digit.health.sync.context.metric.SyncStepMetric;
import org.digit.health.sync.context.step.DeliverySyncStep;
import org.digit.health.sync.context.step.RegistrationSyncStep;
import org.digit.health.sync.context.step.SyncStep;
import org.digit.health.sync.helper.ResourceDeliveryRequestTestBuilder;
import org.digit.health.sync.helper.HouseholdRegistrationRequestTestBuilder;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.digit.health.sync.utils.Properties;
import org.digit.health.sync.web.models.request.ResourceDeliveryRequest;
import org.digit.health.sync.web.models.request.HouseholdRegistrationRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthCampaignSyncOrchestratorTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private Properties properties;

    @Mock
    private ServiceRequestRepository serviceRequestRepository;

    @Test
    @DisplayName("health campaign orchestrator should orchestrate sync and return sync metrics")
    void testThatGivenAPayloadOrchestratorCanOrchestrateSyncAndReturnMetrics() {
        SyncOrchestrator<Map<Class<? extends SyncStep>, Object>, List<SyncStepMetric>>
                syncOrchestrator = new HealthCampaignSyncOrchestrator(applicationContext);
        Map<Class<? extends SyncStep>, Object> stepToPayloadMap = new HashMap<>();
        stepToPayloadMap.put(RegistrationSyncStep.class,
                HouseholdRegistrationRequestTestBuilder.builder().withDummyClientReferenceId().build());
        stepToPayloadMap.put(DeliverySyncStep.class,
                ResourceDeliveryRequestTestBuilder.builder().withDummyClientReferenceId().build());
        when(applicationContext.getBean(HealthCampaignSyncContext.class))
                .thenReturn(new HealthCampaignSyncContext(new RegistrationSyncStep(applicationContext)));
        when(applicationContext.getBean(Properties.class)).thenReturn(properties);
        when(applicationContext.getBean(ServiceRequestRepository.class)).thenReturn(serviceRequestRepository);
        when(applicationContext.getBean(DeliverySyncStep.class))
                .thenReturn(new DeliverySyncStep(applicationContext));
        when(properties.getRegistrationBaseUrl()).thenReturn("some-url");
        when(properties.getRegistrationCreateEndpoint()).thenReturn("some-endpoint");
        when(properties.getDeliveryBaseUrl()).thenReturn("some-url");
        when(properties.getDeliveryCreateEndpoint()).thenReturn("some-endpoint");

        List<SyncStepMetric> result = syncOrchestrator.orchestrate(stepToPayloadMap);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("health campaign orchestrator should not handle next step in case of error before step handling")
    void testThatOrchestratorShouldNotHandleNextStepInCaseOfErrorBeforeStepHandling() {
        SyncOrchestrator<Map<Class<? extends SyncStep>, Object>, List<SyncStepMetric>>
                syncOrchestrator = new HealthCampaignSyncOrchestrator(applicationContext);
        Map<Class<? extends SyncStep>, Object> stepToPayloadMap = new HashMap<>();
        stepToPayloadMap.put(RegistrationSyncStep.class,
                HouseholdRegistrationRequestTestBuilder.builder().withDummyClientReferenceId().build());
        stepToPayloadMap.put(DeliverySyncStep.class,
                ResourceDeliveryRequestTestBuilder.builder().withDummyClientReferenceId().build());
        HealthCampaignSyncContext healthCampaignSyncContext = Mockito.mock(HealthCampaignSyncContext.class);
        when(applicationContext.getBean(HealthCampaignSyncContext.class))
                .thenReturn(healthCampaignSyncContext);
        when(healthCampaignSyncContext.getSyncStep()).thenReturn(new RegistrationSyncStep(applicationContext));
        doThrow(CustomException.class).when(healthCampaignSyncContext)
                .handle(stepToPayloadMap.get(RegistrationSyncStep.class));

        List<SyncStepMetric> result = syncOrchestrator.orchestrate(stepToPayloadMap);

        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("health campaign orchestrator should not handle next step in case of error in a step")
    void testThatOrchestratorShouldNotHandleNextStepInCaseOfErrorInAStep() {
        SyncOrchestrator<Map<Class<? extends SyncStep>, Object>, List<SyncStepMetric>>
                syncOrchestrator = new HealthCampaignSyncOrchestrator(applicationContext);
        Map<Class<? extends SyncStep>, Object> stepToPayloadMap = new HashMap<>();
        HouseholdRegistrationRequest householdRegistrationRequest =
                HouseholdRegistrationRequestTestBuilder.builder()
                        .withDummyClientReferenceId().build();
        ResourceDeliveryRequest resourceDeliveryRequest = ResourceDeliveryRequestTestBuilder.builder()
                .withDummyClientReferenceId().build();
        stepToPayloadMap.put(RegistrationSyncStep.class,
                householdRegistrationRequest);
        stepToPayloadMap.put(DeliverySyncStep.class,
                resourceDeliveryRequest);
        when(applicationContext.getBean(HealthCampaignSyncContext.class))
                .thenReturn(new HealthCampaignSyncContext(new RegistrationSyncStep(applicationContext)));
        when(applicationContext.getBean(Properties.class)).thenReturn(properties);
        when(applicationContext.getBean(ServiceRequestRepository.class)).thenReturn(serviceRequestRepository);
        when(properties.getRegistrationBaseUrl()).thenReturn("some-registration-url");
        when(properties.getRegistrationCreateEndpoint()).thenReturn("some-registration-endpoint");
        lenient().doThrow(new CustomException()).when(serviceRequestRepository).fetchResult(
                any(StringBuilder.class),
                any(HouseholdRegistrationRequest.class), eq(ResponseEntity.class));

        List<SyncStepMetric> result = syncOrchestrator.orchestrate(stepToPayloadMap);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("health campaign orchestrator should return metrics of the steps executed")
    void testThatOrchestratorReturnMetricsOfTheStepsExecuted() {
        SyncOrchestrator<Map<Class<? extends SyncStep>, Object>, List<SyncStepMetric>>
                syncOrchestrator = new HealthCampaignSyncOrchestrator(applicationContext);
        Map<Class<? extends SyncStep>, Object> stepToPayloadMap = new HashMap<>();
        HouseholdRegistrationRequest householdRegistrationRequest =
                HouseholdRegistrationRequestTestBuilder.builder()
                        .withDummyClientReferenceId().build();
        ResourceDeliveryRequest resourceDeliveryRequest = ResourceDeliveryRequestTestBuilder.builder()
                .withDummyClientReferenceId().build();
        stepToPayloadMap.put(RegistrationSyncStep.class,
                householdRegistrationRequest);
        stepToPayloadMap.put(DeliverySyncStep.class, resourceDeliveryRequest);
        when(applicationContext.getBean(HealthCampaignSyncContext.class))
                .thenReturn(new HealthCampaignSyncContext(new RegistrationSyncStep(applicationContext)));
        when(applicationContext.getBean(Properties.class)).thenReturn(properties);
        when(applicationContext.getBean(ServiceRequestRepository.class)).thenReturn(serviceRequestRepository);
        when(applicationContext.getBean(DeliverySyncStep.class))
                .thenReturn(new DeliverySyncStep(applicationContext));
        when(properties.getRegistrationBaseUrl()).thenReturn("some-registration-url");
        when(properties.getRegistrationCreateEndpoint()).thenReturn("some-registration-endpoint");
        when(properties.getDeliveryBaseUrl()).thenReturn("some-url");
        when(properties.getDeliveryCreateEndpoint()).thenReturn("some-endpoint");
        lenient().doReturn(ResponseEntity.ok()).when(serviceRequestRepository).fetchResult(
                any(StringBuilder.class),
                any(HouseholdRegistrationRequest.class), eq(ResponseEntity.class));
        lenient().doThrow(new CustomException()).when(serviceRequestRepository).fetchResult(
                any(StringBuilder.class), any(ResourceDeliveryRequest.class), eq(ResponseEntity.class));

        List<SyncStepMetric> result = syncOrchestrator.orchestrate(stepToPayloadMap);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("health campaign orchestrator should allow to execute adhoc steps - retry scenario")
    void testThatOrchestratorShouldAllowExecutionOfAdhocSteps() {
        SyncOrchestrator<Map<Class<? extends SyncStep>, Object>, List<SyncStepMetric>>
                syncOrchestrator = new HealthCampaignSyncOrchestrator(applicationContext);
        Map<Class<? extends SyncStep>, Object> stepToPayloadMap = new HashMap<>();
        ResourceDeliveryRequest resourceDeliveryRequest = ResourceDeliveryRequestTestBuilder.builder()
                .withDummyClientReferenceId().build();
        stepToPayloadMap.put(DeliverySyncStep.class, resourceDeliveryRequest);
        HealthCampaignSyncContext healthCampaignSyncContext = Mockito
                .spy(new HealthCampaignSyncContext(new RegistrationSyncStep(applicationContext)));
        when(applicationContext.getBean(HealthCampaignSyncContext.class))
                .thenReturn(healthCampaignSyncContext);
        when(applicationContext.getBean(Properties.class)).thenReturn(properties);
        when(applicationContext.getBean(ServiceRequestRepository.class)).thenReturn(serviceRequestRepository);
        when(applicationContext.getBean(DeliverySyncStep.class))
                .thenReturn(new DeliverySyncStep(applicationContext));
        when(properties.getDeliveryBaseUrl()).thenReturn("some-url");
        when(properties.getDeliveryCreateEndpoint()).thenReturn("some-endpoint");
        lenient().doReturn(ResponseEntity.ok()).when(serviceRequestRepository).fetchResult(
                any(StringBuilder.class), any(ResourceDeliveryRequest.class), eq(ResponseEntity.class));

        List<SyncStepMetric> result = syncOrchestrator.orchestrate(stepToPayloadMap);

        verify(healthCampaignSyncContext, times(1)).handle(any());

        assertEquals(1, result.size());
    }
}
