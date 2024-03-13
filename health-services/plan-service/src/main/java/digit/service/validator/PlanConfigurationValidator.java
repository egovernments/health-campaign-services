package digit.service.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import digit.util.MdmsUtil;
import digit.web.models.Assumption;
import digit.web.models.Operation;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.PlanConfigurationSearchRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static digit.config.ServiceConstants.ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_CODE;
import static digit.config.ServiceConstants.ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_MESSAGE;
import static digit.config.ServiceConstants.ASSUMPTION_VALUE_NOT_FOUND_CODE;
import static digit.config.ServiceConstants.ASSUMPTION_VALUE_NOT_FOUND_MESSAGE;
import static digit.config.ServiceConstants.MDMS_MASTER_ASSUMPTION;
import static digit.config.ServiceConstants.MDMS_PLAN_ASSUMPTION_MODULE_NAME;
import static digit.config.ServiceConstants.MDMS_TENANT_MODULE_NAME;
import static digit.config.ServiceConstants.MDSM_MASTER_TENANTS;
import static digit.config.ServiceConstants.TENANT_NOT_FOUND_IN_MDMS_CODE;
import static digit.config.ServiceConstants.TENANT_NOT_FOUND_IN_MDMS_MESSAGE;

@Component
@Slf4j
public class PlanConfigurationValidator {


    private MdmsUtil mdmsUtil;
    private ObjectMapper objectMapper;

    public PlanConfigurationValidator(MdmsUtil mdmsUtil, ObjectMapper objectMapper) {
        this.mdmsUtil = mdmsUtil;
        this.objectMapper = objectMapper;
    }

    public void validateCreate(PlanConfigurationRequest request) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        String rootTenantId = planConfiguration.getTenantId().split("\\.")[0];
        Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(), rootTenantId);

        validateAssumptionKeyAgainstMDMS(request, mdmsData);
        validateAssumptionValue(planConfiguration);
    }

    public void validateAssumptionValue(PlanConfiguration planConfiguration) {
        Set<String> assumptionValues = planConfiguration.getAssumptions().stream()
                .map(Assumption::getKey)
                .collect(Collectors.toSet());

        List<Operation> operations = planConfiguration.getOperations();
        for (Operation operation : operations) {
            if (!assumptionValues.contains(operation.getAssumptionValue())) {
                log.error("Assumption Value " + operation.getAssumptionValue() + " is not present in Assumption Key List");
                throw new CustomException(ASSUMPTION_VALUE_NOT_FOUND_CODE, ASSUMPTION_VALUE_NOT_FOUND_MESSAGE);
            }
        }
    }

    public void validateAssumptionKeyAgainstMDMS(PlanConfigurationRequest request, Object mdmsData) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        final String jsonPathForAssumption = "$." + MDMS_PLAN_ASSUMPTION_MODULE_NAME + "." + MDMS_MASTER_ASSUMPTION + ".*";

        List<Object> assumptionListFromMDMS = null;
        try {
            log.info(jsonPathForAssumption);
            assumptionListFromMDMS = JsonPath.read(mdmsData, jsonPathForAssumption);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException("JSONPATH_ERROR", "Failed to parse mdms response");
        }

        for(Assumption assumption : planConfiguration.getAssumptions())
        {
            if(!assumptionListFromMDMS.contains(assumption.getKey()))
            {
                log.error("Assumption Key " + assumption.getKey() + " is not present in MDMS");
                throw new CustomException(ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_CODE, ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_MESSAGE);
            }
        }
    }

    public void validateSearchRequest(PlanConfigurationSearchRequest planConfigurationSearchRequest) {
        validateSearchCriteria(planConfigurationSearchRequest);
    }

    private void validateSearchCriteria(PlanConfigurationSearchRequest planConfigurationSearchRequest) {
        if (Objects.isNull(planConfigurationSearchRequest.getPlanConfigurationSearchCriteria())) {
            throw new CustomException("SEARCH CRITERIA CANNOT BE EMPTY", "Search criteria cannot be empty");
        }

        if (StringUtils.isEmpty(planConfigurationSearchRequest.getPlanConfigurationSearchCriteria().getTenantId())) {
            throw new CustomException("TENANT ID CANNOT BE EMPTY", "Tenant Id cannot be empty, TenantId should be present");
        }
    }
}
