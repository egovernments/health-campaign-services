package org.egov.processor.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.repository.ServiceRequestRepository;
import org.springframework.stereotype.Component;
import org.egov.processor.web.models.planFacility.*;

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

    /**
     * Searches for plan facilities based on the provided search request.
     *
     * @param planfacilitySearchRequest The search request containing the search criteria.
     * @return A response with a list of plan facilities that matches the search criteria.
     */
    public PlanFacilityResponse search(PlanFacilitySearchRequest planfacilitySearchRequest) {

        PlanFacilityResponse planFacilityResponse = null;
        try {
            Object response = serviceRequestRepository.fetchResult(getPlanFacilitySearchUri(), planfacilitySearchRequest);
            planFacilityResponse = mapper.convertValue(response, PlanFacilityResponse.class);

        } catch (Exception e) {
            log.error(ServiceConstants.ERROR_WHILE_SEARCHING_PLAN_FACILITY, e);
        }

        return planFacilityResponse;
    }

    /**
     * Creates a search uri for plan facility search.
     * @return
     */
    private StringBuilder getPlanFacilitySearchUri() {
        return new StringBuilder().append(config.getPlanConfigHost()).append(config.getPlanFacilitySearchEndPoint());
    }
}
