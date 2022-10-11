package org.digit.health.sync.orchestrator;

import org.digit.health.sync.context.HealthCampaignSyncContext;
import org.digit.health.sync.context.metric.SyncMetric;
import org.digit.health.sync.context.step.DeliverySyncStep;
import org.digit.health.sync.context.step.RegistrationSyncStep;
import org.digit.health.sync.context.step.SyncStep;
import org.digit.health.sync.helper.DeliveryRequestTestBuilder;
import org.digit.health.sync.helper.RegistrationRequestTestBuilder;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.digit.health.sync.utils.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        SyncOrchestrator syncOrchestrator = new HealthCampaignSyncOrchestrator(applicationContext);
        Map<Class<? extends SyncStep>, Object> stepToPayloadMap = new HashMap<>();
        stepToPayloadMap.put(RegistrationSyncStep.class,
                RegistrationRequestTestBuilder.builder().withDummyClientReferenceId().build());
        stepToPayloadMap.put(DeliverySyncStep.class,
                DeliveryRequestTestBuilder.builder().withDummyClientReferenceId().build());
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

        Object result = syncOrchestrator.orchestrate(stepToPayloadMap);

        assertEquals(2, ((List<SyncMetric>) result).size());
    }
}
