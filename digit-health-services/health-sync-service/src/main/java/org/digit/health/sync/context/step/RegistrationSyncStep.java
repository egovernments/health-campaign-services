package org.digit.health.sync.context.step;

import org.digit.health.sync.context.enums.RecordIdType;
import org.digit.health.sync.context.enums.StepSyncStatus;
import org.digit.health.sync.context.SyncContext;
import org.digit.health.sync.context.metric.SyncMetric;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.digit.health.sync.utils.Properties;
import org.digit.health.sync.web.models.request.RegistrationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component("registrationSyncStep")
public class RegistrationSyncStep extends SyncStep {

    private final ApplicationContext applicationContext;


    @Autowired
    public RegistrationSyncStep(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void nextSyncStep(SyncContext syncContext) {
        syncContext.setSyncStep(applicationContext.getBean(DeliverySyncStep.class));
    }

    @Override
    public void handle(Object payload) {
        RegistrationRequest registrationRequest = (RegistrationRequest) payload;
        ServiceRequestRepository serviceRequestRepository = applicationContext
                .getBean(ServiceRequestRepository.class);
        Properties properties = applicationContext.getBean(Properties.class);
        serviceRequestRepository.fetchResult(new StringBuilder(properties.getRegistrationBaseUrl()
                + properties.getRegistrationCreateEndpoint()),
                registrationRequest, ResponseEntity.class);
        this.setChanged();
        this.notifyObservers(SyncMetric.builder()
                .status(StepSyncStatus.COMPLETED)
                .recordId(registrationRequest.getClientReferenceId())
                .recordIdType(RecordIdType.REGISTRATION)
                .build());
    }

    @Override
    public boolean hasNext() {
        return true;
    }
}
