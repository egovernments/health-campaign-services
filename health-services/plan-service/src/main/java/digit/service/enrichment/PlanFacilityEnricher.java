package digit.service.enrichment;

import digit.util.CensusUtil;
import digit.util.CommonUtil;
import digit.web.models.PlanFacility;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.PlanFacilitySearchCriteria;
import digit.web.models.PlanFacilitySearchRequest;
import digit.web.models.census.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.AuditDetailsEnrichmentUtil;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;
import static org.egov.common.utils.AuditDetailsEnrichmentUtil.prepareAuditDetails;

@Component
@Slf4j
public class PlanFacilityEnricher {

    private CommonUtil commonUtil;

    private CensusUtil censusUtil;

    public PlanFacilityEnricher(CommonUtil commonUtil, CensusUtil censusUtil) {
        this.commonUtil = commonUtil;
        this.censusUtil = censusUtil;
    }

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

        // Add plan config name to which the facility is mapped
        planFacilityRequest.getPlanFacility().setPlanConfigurationName(commonUtil.getPlanConfigName(planFacilityRequest.getPlanFacility().getTenantId(), planFacilityRequest.getPlanFacility().getPlanConfigurationId()));
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

        // enrich serving population
        enrichServingPopulation(planFacilityRequest);
    }

    /**
     * Enriches serving population based on the serving boundaries provided.
     *
     * @param planFacilityRequest plan facility request whose serving population is to be enriched.
     */
    private void enrichServingPopulation(PlanFacilityRequest planFacilityRequest) {
        PlanFacility planFacility = planFacilityRequest.getPlanFacility();

        // Prepare list of boundaries whose census records are to be fetched
        Set<String> boundariesToBeSearched = new HashSet<>(planFacility.getServiceBoundaries());
        boundariesToBeSearched.addAll(planFacility.getInitiallySetServiceBoundaries());

        if(!CollectionUtils.isEmpty(boundariesToBeSearched)) {
            CensusSearchCriteria censusSearchCriteria = CensusSearchCriteria.builder()
                    .tenantId(planFacility.getTenantId())
                    .source(planFacility.getPlanConfigurationId())
                    .areaCodes(new ArrayList<>(boundariesToBeSearched))
                    .limit(boundariesToBeSearched.size())
                    .build();

            CensusResponse censusResponse = censusUtil.fetchCensusRecords(CensusSearchRequest.builder()
                    .requestInfo(planFacilityRequest.getRequestInfo())
                    .censusSearchCriteria(censusSearchCriteria)
                    .build());

            // Creates a population map based on the confirmed target population of the boundary
            Map<String, Long> boundaryToPopMap = getPopulationMap(censusResponse.getCensus());

            // Get existing servingPopulation or default to 0
            BigDecimal servingPopulation = (BigDecimal) commonUtil.extractFieldsFromJsonObject(planFacility.getAdditionalDetails(), SERVING_POPULATION_CODE);

            updateServingPopulation(boundariesToBeSearched, planFacility, boundaryToPopMap, servingPopulation);
        }
    }

    /**
     * Creates a mapping of boundary with it's confirmed target population.
     *
     * @param censusList Census records for the given list of serving boundaries.
     * @return returns a map of boundary with its confirmed target population.
     */
    private Map<String, Long> getPopulationMap(List<Census> censusList) {
        Map<String, Long> boundaryToPopMap = new HashMap<>();

        for (Census census : censusList) {
            Map<String, BigDecimal> additionalFieldsMap = census.getAdditionalFields().stream()
                    .collect(Collectors.toMap(AdditionalField::getKey, AdditionalField::getValue));

            Long confirmedTargetPopulation = 0L;

            // Get confirmed target population based on campaign type.
            if (additionalFieldsMap.containsKey(CONFIRMED_TARGET_POPULATION_AGE_3TO11)) {
                confirmedTargetPopulation = additionalFieldsMap.get(CONFIRMED_TARGET_POPULATION_AGE_3TO11)
                        .add(additionalFieldsMap.get(CONFIRMED_TARGET_POPULATION_AGE_12TO59))
                        .longValue();
            } else if(additionalFieldsMap.containsKey(CONFIRMED_TARGET_POPULATION)){
                confirmedTargetPopulation = additionalFieldsMap.get(CONFIRMED_TARGET_POPULATION).longValue();
            }

            // Map the boundary code with it's confirmed target population.
            boundaryToPopMap.put(census.getBoundaryCode(), confirmedTargetPopulation);
        }

        return boundaryToPopMap;
    }

    private void updateServingPopulation(Set<String> boundariesToBeSearched, PlanFacility planFacility, Map<String, Long> boundaryToPopMap, BigDecimal servingPopulation) {
        Set<String> currentServiceBoundaries = new HashSet<>(planFacility.getServiceBoundaries());
        Set<String> initialServiceBoundaries = new HashSet<>(planFacility.getInitiallySetServiceBoundaries());

        for(String boundary : boundariesToBeSearched) {
            Long totalPopulation = boundaryToPopMap.get(boundary);

            if (!currentServiceBoundaries.contains(boundary)) {
                servingPopulation = servingPopulation.subtract(BigDecimal.valueOf(totalPopulation));
            } else if (!initialServiceBoundaries.contains(boundary)) {
                servingPopulation = servingPopulation.add(BigDecimal.valueOf(totalPopulation));
            }
        }
        Map<String, Object> fieldToUpdate = new HashMap<>();
        fieldToUpdate.put(SERVING_POPULATION_CODE, servingPopulation);

        planFacility.setAdditionalDetails(commonUtil.updateFieldInAdditionalDetails(planFacility.getAdditionalDetails(), fieldToUpdate));
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