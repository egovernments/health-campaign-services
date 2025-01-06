package org.egov.processor.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.web.models.planFacility.PlanFacilityResponse;
import org.egov.processor.web.models.planFacility.PlanFacilitySearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.processor.config.ServiceConstants.*;

@Slf4j
@Component
public class PlanFacilityUtil {
    private Configuration config;

    private ServiceRequestRepository serviceRequestRepository;

    private ObjectMapper mapper;

    public PlanFacilityUtil(Configuration config, ServiceRequestRepository serviceRequestRepository, ObjectMapper mapper) {
        this.config = config;
        this.serviceRequestRepository = serviceRequestRepository;
        this.mapper = mapper;
    }

    public PlanFacilityResponse search(PlanFacilitySearchRequest planfacilitySearchRequest) {

        PlanFacilityResponse planFacilityResponse = null;
        try {
            Object response = serviceRequestRepository.fetchResult(getPlanFacilitySearchUri(), planfacilitySearchRequest);
            planFacilityResponse = mapper.convertValue(response, PlanFacilityResponse.class);
        } catch (Exception e) {
            log.error(ServiceConstants.ERROR_WHILE_SEARCHING_PLAN_FACILITY);
        }

        if (CollectionUtils.isEmpty(planFacilityResponse.getPlanFacility())) {
            throw new CustomException(NO_PLAN_FACILITY_FOUND_FOR_GIVEN_DETAILS_CODE, NO_PLAN_FACILITY_FOUND_FOR_GIVEN_DETAILS_MESSAGE);
        }

        return planFacilityResponse;
    }

    /**
     * Creates a complete search uri for plan facility search.
     * @return
     */
    private StringBuilder getPlanFacilitySearchUri() {
        return new StringBuilder().append(config.getPlanConfigHost()).append(config.getPlanFacilitySearchEndPoint());
    }
}
