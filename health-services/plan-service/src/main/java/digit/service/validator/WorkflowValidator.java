package digit.service.validator;

import digit.service.PlanService;
import digit.util.CensusUtil;
import digit.util.CommonUtil;
import digit.web.models.*;
import digit.web.models.census.CensusResponse;
import digit.web.models.census.CensusSearchCriteria;
import digit.web.models.census.CensusSearchRequest;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Map;

import static digit.config.ServiceConstants.*;
import static digit.config.ServiceConstants.CANNOT_APPROVE_ESTIMATIONS_MESSAGE;

@Component
public class WorkflowValidator {

    private CensusUtil censusUtil;

    private PlanService planService;

    private CommonUtil commonUtil;

    public WorkflowValidator(CensusUtil censusUtil, PlanService planService, CommonUtil commonUtil) {
        this.censusUtil = censusUtil;
        this.planService = planService;
        this.commonUtil = commonUtil;
    }

    /**
     * Validates the workflow action in the plan configuration request and
     * triggers the corresponding validation method if applicable.
     *
     * @param planConfigurationRequest the request containing workflow details.
     */
    public void validateWorkflow(PlanConfigurationRequest planConfigurationRequest) {
        if (ObjectUtils.isEmpty(planConfigurationRequest.getPlanConfiguration().getWorkflow()))
            return;

        String workflowAction = planConfigurationRequest.getPlanConfiguration().getWorkflow().getAction();

        switch (workflowAction) {
            case APPROVE_CENSUS_DATA_ACTION:
                validateCensusData(planConfigurationRequest);
                break;
            case FINALIZE_CATCHMENT_MAPPING_ACTION:
                validateCatchmentMapping(planConfigurationRequest);
                break;
            case APPROVE_ESTIMATIONS_ACTION:
                validateResourceEstimations(planConfigurationRequest);
                break;
        }
    }

    /**
     * Validates if all the census records are validated before approving census data for the given planConfigId.
     *
     * @param planConfigurationRequest request with plan config id.
     */
    private void validateCensusData(PlanConfigurationRequest planConfigurationRequest) {
        PlanConfiguration planConfiguration = planConfigurationRequest.getPlanConfiguration();

        CensusSearchRequest censusSearchRequest = commonUtil.getCensusSearchRequest(planConfiguration.getTenantId(), planConfiguration.getId(), planConfigurationRequest.getRequestInfo());

        // Fetches census records for given planConfigId
        CensusResponse censusResponse = censusUtil.fetchCensusRecords(censusSearchRequest);

        Map<String, Integer> statusCount = censusResponse.getStatusCount();
        Integer totalCount = censusResponse.getTotalCount();

        // Throws exception if all census records are not validated
        if (!statusCount.get(VALIDATED_STATUS).equals(totalCount)) {
            throw new CustomException(CANNOT_APPROVE_CENSUS_DATA_CODE, CANNOT_APPROVE_CENSUS_DATA_MESSAGE);
        }
    }

    /**
     * Validates if all boundaries have facility assigned before finalizing catchment mapping for a given planConfigID.
     *
     * @param planConfigurationRequest request with plan config id.
     */
    private void validateCatchmentMapping(PlanConfigurationRequest planConfigurationRequest) {
        PlanConfiguration planConfiguration = planConfigurationRequest.getPlanConfiguration();

        CensusSearchRequest censusSearchRequest = commonUtil.getCensusSearchRequest(planConfiguration.getTenantId(), planConfiguration.getId(), planConfigurationRequest.getRequestInfo());

        // Fetches all census records for given planConfigId
        CensusResponse censusResponse = censusUtil.fetchCensusRecords(censusSearchRequest);
        Integer totalCensusCount = censusResponse.getTotalCount();

        censusSearchRequest.getCensusSearchCriteria().setFacilityAssigned(Boolean.TRUE);

        // Fetches all census records for given planConfigId where facility is assigned
        CensusResponse censusWithFacilityAssigned = censusUtil.fetchCensusRecords(censusSearchRequest);
        Integer totalCensusWithFacilityAssigned = censusWithFacilityAssigned.getTotalCount();

        if (!totalCensusCount.equals(totalCensusWithFacilityAssigned)) {
            throw new CustomException(CANNOT_FINALIZE_CATCHMENT_MAPPING_CODE, CANNOT_FINALIZE_CATCHMENT_MAPPING_MESSAGE);
        }
    }

    /**
     * Validates if all the plan estimations are validated before approving estimations for the given planConfigId.
     *
     * @param planConfigurationRequest request with plan config id.
     */
    private void validateResourceEstimations(PlanConfigurationRequest planConfigurationRequest) {
        PlanConfiguration planConfiguration = planConfigurationRequest.getPlanConfiguration();

        PlanSearchRequest searchRequest = commonUtil.getPlanSearchRequest(planConfiguration.getTenantId(), planConfiguration.getId(), planConfigurationRequest.getRequestInfo());

        // Fetches plans for given planConfigId
        PlanResponse planResponse = planService.searchPlan(searchRequest);

        Map<String, Integer> statusCount = planResponse.getStatusCount();
        Integer totalCount = planResponse.getTotalCount();

        // Throws exception if all plans are not validated
        if (!statusCount.get(VALIDATED_STATUS).equals(totalCount)) {
            throw new CustomException(CANNOT_APPROVE_ESTIMATIONS_CODE, CANNOT_APPROVE_ESTIMATIONS_MESSAGE);
        }
    }

}
