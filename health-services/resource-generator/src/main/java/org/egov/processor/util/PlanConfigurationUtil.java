package org.egov.processor.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.config.Configuration;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.web.models.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static org.egov.processor.config.ServiceConstants.ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE;

@Component
@Slf4j
public class PlanConfigurationUtil {

    private ServiceRequestRepository serviceRequestRepository;

    private Configuration config;

    private ObjectMapper mapper;

    public PlanConfigurationUtil(ServiceRequestRepository serviceRequestRepository, Configuration config, ObjectMapper mapper) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.mapper = mapper;
    }

    public List<PlanConfiguration> search(PlanConfigurationSearchRequest planConfigurationSearchRequest)
    {
        List<PlanConfiguration> planConfigurationList = new ArrayList<>();
        PlanConfigurationResponse planConfigurationResponse = null;
        Object response = new HashMap<>();

        StringBuilder uri = new StringBuilder();
        uri.append(config.getPlanConfigHost()).append(config.getPlanConfigEndPoint());

        try {
            response = serviceRequestRepository.fetchResult(uri, planConfigurationSearchRequest);
            planConfigurationResponse = mapper.convertValue(response, PlanConfigurationResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE, e);
        }


        if(planConfigurationResponse != null)
            return planConfigurationResponse.getPlanConfiguration();
        else
            return planConfigurationList;
    }

    public void orderPlanConfigurationOperations(PlanConfigurationRequest planConfigurationRequest) {
        planConfigurationRequest.getPlanConfiguration().getOperations().sort(Comparator.comparingInt(Operation::getExecutionOrder));
    }
}
