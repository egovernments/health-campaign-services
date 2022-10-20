package org.digit.health.sync.context.step;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.context.SyncContext;
import org.digit.health.sync.context.enums.RecordIdType;
import org.digit.health.sync.context.enums.SyncErrorCode;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.digit.health.sync.utils.Properties;
import org.digit.health.sync.web.models.request.HouseholdRegistrationRequest;
import org.digit.health.sync.web.models.response.RegistrationResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
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
        List<HouseholdRegistrationRequest> list = new ArrayList<>();
        HouseholdRegistrationRequest householdRegistrationRequest = (HouseholdRegistrationRequest) payload;
        list.add(householdRegistrationRequest);
        ServiceRequestRepository serviceRequestRepository = applicationContext
                .getBean(ServiceRequestRepository.class);
        Properties properties = applicationContext.getBean(Properties.class);
        try {
            serviceRequestRepository.fetchResult(new StringBuilder(properties.getRegistrationBaseUrl()
                            + properties.getRegistrationCreateEndpoint()),
                    list, RegistrationResponse.class);
        } catch (Exception exception) {
            log.error("Exception occurred", exception);
            publishFailureMetric(householdRegistrationRequest
                            .getHousehold().getClientReferenceId(),
                    RecordIdType.REGISTRATION, exception.getMessage());
            throw new CustomException(SyncErrorCode.ERROR_IN_REST_CALL.name(),
                    SyncErrorCode.ERROR_IN_REST_CALL.message(exception.getMessage()));
        }
        publishSuccessMetric(householdRegistrationRequest
                        .getHousehold().getClientReferenceId(),
                RecordIdType.REGISTRATION);
    }

    @Override
    public boolean hasNext() {
        return true;
    }
}
