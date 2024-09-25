package digit.service.validator;

import digit.repository.PlanConfigurationRepository;
import digit.util.MdmsUtil;
import digit.web.models.PlanConfigurationSearchCriteria;
import digit.web.models.PlanFacilityRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import static digit.config.ServiceConstants.INVALID_PLAN_CONFIG_ID_CODE;
import static digit.config.ServiceConstants.INVALID_PLAN_CONFIG_ID_MESSAGE;

@Component
@Slf4j
public class PlanFacilityValidator {

    private MdmsUtil mdmsUtil;

    private MultiStateInstanceUtil centralInstanceUtil;

    private PlanConfigurationRepository planConfigurationRepository;

    public PlanFacilityValidator(MdmsUtil mdmsUtil, MultiStateInstanceUtil centralInstanceUtil, PlanConfigurationRepository planConfigurationRepository) {
        this.mdmsUtil = mdmsUtil;
        this.centralInstanceUtil = centralInstanceUtil;
        this.planConfigurationRepository = planConfigurationRepository;
    }

    public void validatePlanFacilityCreate(@Valid PlanFacilityRequest planFacilityRequest) {
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(planFacilityRequest.getPlanFacility().getTenantId());

        // Validate plan configuration existence
        validatePlanConfigurationExistence(planFacilityRequest);

    }

    private void validatePlanConfigurationExistence(PlanFacilityRequest request) {
        // If plan configuration id provided is invalid, throw an exception
        if(!ObjectUtils.isEmpty(request.getPlanFacility().getPlanConfigurationId()) && CollectionUtils.isEmpty(
                planConfigurationRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(request.getPlanFacility().getPlanConfigurationId())
                .tenantId(request.getPlanFacility().getTenantId())
                .build()))) {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
        }
    }
}
