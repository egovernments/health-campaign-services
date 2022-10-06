package org.digit.health.sync.context.step;

import org.digit.health.sync.context.LlinSyncContext;
import org.digit.health.sync.context.SyncContext;
import org.digit.health.sync.context.step.RegistrationSyncStep;
import org.digit.health.sync.context.step.SyncStep;
import org.digit.health.sync.helper.RegistrationRequestTestBuilder;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.digit.health.sync.utils.Properties;
import org.digit.health.sync.web.models.request.RegistrationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;

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

    @Test
    @DisplayName("registration sync step should call registration service")
    void testThatRegistrationSyncStepShouldCallRegistrationService() {
        SyncStep registrationSyncStep = new RegistrationSyncStep(applicationContext);
        SyncContext syncContext = new LlinSyncContext(registrationSyncStep);
        registrationSyncStep.addObserver(syncContext);
        when(applicationContext.getBean(ServiceRequestRepository.class))
                .thenReturn(serviceRequestRepository);
        when(applicationContext.getBean(Properties.class)).thenReturn(properties);
        when(properties.getRegistrationBaseUrl()).thenReturn("some-url");
        when(properties.getRegistrationCreateEndpoint()).thenReturn("some-endpoint");

        registrationSyncStep.handle(RegistrationRequestTestBuilder
                .builder()
                .withDummyClientReferenceId()
                .build());

        verify(serviceRequestRepository, times(1))
                .fetchResult(any(StringBuilder.class),
                any(RegistrationRequest.class),
                eq(ResponseEntity.class));
    }
}
