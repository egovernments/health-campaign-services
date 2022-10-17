package org.digit.health.sync.context.step;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.context.SyncContext;
import org.digit.health.sync.context.enums.RecordIdType;
import org.digit.health.sync.context.enums.SyncErrorCode;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.digit.health.sync.utils.Properties;
import org.digit.health.sync.web.models.request.ResourceDeliveryRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeliverySyncStep extends SyncStep {
    private final ApplicationContext applicationContext;

    @Autowired
    public DeliverySyncStep(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void nextSyncStep(SyncContext syncContext) {
        // no next step after this
    }

    @Override
    public void handle(Object payload) {
        ResourceDeliveryRequest resourceDeliveryRequest = (ResourceDeliveryRequest) payload;
        ServiceRequestRepository serviceRequestRepository = applicationContext
                .getBean(ServiceRequestRepository.class);
        Properties properties = applicationContext.getBean(Properties.class);
        try {
            serviceRequestRepository.fetchResult(new StringBuilder(properties.getDeliveryBaseUrl()
                            + properties.getDeliveryCreateEndpoint()),
                    resourceDeliveryRequest, ResponseEntity.class);
        } catch (Exception exception) {
            log.error("Exception while calling registration service", exception);
            publishFailureMetric(resourceDeliveryRequest.getDelivery().getClientReferenceId(),
                    RecordIdType.DELIVERY, exception.getMessage());
            throw new CustomException(SyncErrorCode.ERROR_IN_REST_CALL.name(),
                    SyncErrorCode.ERROR_IN_REST_CALL.message(exception.getMessage()));
        }
        publishSuccessMetric(resourceDeliveryRequest.getDelivery().getClientReferenceId(),
                RecordIdType.DELIVERY);
    }

    @Override
    public boolean hasNext() {
        return false;
    }
}
