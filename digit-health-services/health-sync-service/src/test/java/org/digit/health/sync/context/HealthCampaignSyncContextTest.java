package org.digit.health.sync.context;

import org.digit.health.sync.context.step.DeliverySyncStep;
import org.digit.health.sync.context.step.RegistrationSyncStep;
import org.digit.health.sync.context.step.SyncStep;
import org.digit.health.sync.helper.DeliveryRequestTestBuilder;
import org.digit.health.sync.helper.RegistrationRequestTestBuilder;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.digit.health.sync.utils.Properties;
import org.digit.health.sync.web.models.request.DeliveryRequest;
import org.digit.health.sync.web.models.request.RegistrationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthCampaignSyncContextTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ServiceRequestRepository serviceRequestRepository;

    @Mock
    private Properties properties;

    void setUpMocks() {
        when(applicationContext.getBean(ServiceRequestRepository.class))
                .thenReturn(serviceRequestRepository);
        when(applicationContext.getBean(Properties.class)).thenReturn(properties);
        when(properties.getRegistrationBaseUrl()).thenReturn("some-url");
        when(properties.getRegistrationCreateEndpoint()).thenReturn("some-endpoint");
        when(properties.getDeliveryBaseUrl()).thenReturn("some-url");
        when(properties.getRegistrationCreateEndpoint()).thenReturn("some-endpoint");
    }

    @Test
    @DisplayName("health campaign sync context should have next step as delivery sync step after registration sync step")
    void testThatHealthCampaignSyncContextHasDeliverySyncStepAfterRegistrationSyncStep() {
        SyncStep registrationSyncStep = new RegistrationSyncStep(applicationContext);
        SyncContext syncContext = new HealthCampaignSyncContext(registrationSyncStep);
        DeliverySyncStep deliverySyncStep = new DeliverySyncStep(applicationContext);
        when(applicationContext.getBean(DeliverySyncStep.class))
                .thenReturn(deliverySyncStep);

        while (syncContext.hasNext()) {
            syncContext.nextSyncStep();
        }

        assertTrue(syncContext.getSyncStep() instanceof DeliverySyncStep);
    }

    @Test
    @DisplayName("health campaign sync context should have metrics provided by steps")
    void testThatHealthCampaignSyncContextHasMetricsProvidedBySteps() {
        SyncStep registrationSyncStep = new RegistrationSyncStep(applicationContext);
        SyncContext syncContext = new HealthCampaignSyncContext(registrationSyncStep);
        DeliverySyncStep deliverySyncStep = new DeliverySyncStep(applicationContext);
        when(applicationContext.getBean(DeliverySyncStep.class))
                .thenReturn(deliverySyncStep);
        setUpMocks();

        RegistrationRequest registrationRequest = RegistrationRequestTestBuilder.builder()
                .withDummyClientReferenceId().build();
        syncContext.handle(registrationRequest);
        syncContext.nextSyncStep();
        DeliveryRequest deliveryRequest = DeliveryRequestTestBuilder
                .builder().withDummyClientReferenceId().build();
        syncContext.handle(deliveryRequest);

        assertEquals(2, syncContext.getSyncMetrics().size());
    }

}