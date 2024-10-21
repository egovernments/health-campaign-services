package digit.service.enrichment;

import digit.web.models.PlanFacility;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.PlanFacilitySearchCriteria;
import digit.web.models.PlanFacilitySearchRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.AuditDetailsEnrichmentUtil;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static digit.config.ServiceConstants.*;
import static org.egov.common.utils.AuditDetailsEnrichmentUtil.prepareAuditDetails;

@Component
@Slf4j
public class PlanFacilityEnricher {

    /**
     * Enriches the plan facility create request
     *
     * @param planFacilityRequest
     */
    public void enrichPlanFacilityCreate(@Valid PlanFacilityRequest planFacilityRequest) {
        // Generate id for plan facility
        UUIDEnrichmentUtil.enrichRandomUuid(planFacilityRequest.getPlanFacility(), "id");

        // Enrich audit details
        planFacilityRequest.getPlanFacility().setAuditDetails(AuditDetailsEnrichmentUtil
                .prepareAuditDetails(planFacilityRequest.getPlanFacility().getAuditDetails(),
                        planFacilityRequest.getRequestInfo(), Boolean.TRUE));

        //Set Active
        planFacilityRequest.getPlanFacility().setActive(Boolean.TRUE);

    }

    /**
     * Enriches the plan facility update request
     *
     * @param planFacilityRequest The PlanFacilityRequest object contains the plan facility to be enriched.
     */
    public void enrichPlanFacilityUpdate(PlanFacilityRequest planFacilityRequest) {
        PlanFacility planFacility = planFacilityRequest.getPlanFacility();

        //enrich audit details
        planFacility.setAuditDetails(prepareAuditDetails(planFacilityRequest.getPlanFacility().getAuditDetails(), planFacilityRequest.getRequestInfo(), Boolean.FALSE));
    }

    /**
     * Enriches plan facility search request
     *
     * @param planFacilitySearchRequest
     */
    public void enrichSearchRequest(PlanFacilitySearchRequest planFacilitySearchRequest) {
        PlanFacilitySearchCriteria planFacilitySearchCriteria = planFacilitySearchRequest.getPlanFacilitySearchCriteria();

        // Filter map for filtering facility meta data present in additional details
        Map<String, String> filtersMap = new LinkedHashMap<>();

        // Add facility name as a filter if present in search criteria
        if(!ObjectUtils.isEmpty(planFacilitySearchCriteria.getFacilityName())) {
            filtersMap.put(FACILITY_NAME_SEARCH_PARAMETER_KEY, planFacilitySearchCriteria.getFacilityName());
        }

        // Add facility status as a filter if present in search criteria
        if(!ObjectUtils.isEmpty(planFacilitySearchCriteria.getFacilityStatus())) {
            filtersMap.put(FACILITY_STATUS_SEARCH_PARAMETER_KEY, planFacilitySearchCriteria.getFacilityStatus());
        }

        // Add facility type as a filter if present in search criteria
        if(!ObjectUtils.isEmpty(planFacilitySearchCriteria.getFacilityType())) {
            filtersMap.put(FACILITY_TYPE_SEARCH_PARAMETER_KEY, planFacilitySearchCriteria.getFacilityType());
        }

        if(!CollectionUtils.isEmpty(filtersMap))
            planFacilitySearchCriteria.setFiltersMap(filtersMap);
    }
}