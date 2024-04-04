package digit.service.validator;

import com.jayway.jsonpath.JsonPath;
import digit.repository.PlanConfigurationRepository;
import digit.util.MdmsUtil;
import digit.web.models.Assumption;
import digit.web.models.File;
import digit.web.models.Operation;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.PlanConfigurationSearchCriteria;
import digit.web.models.PlanConfigurationSearchRequest;
import digit.web.models.ResourceMapping;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static digit.config.ServiceConstants.ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_CODE;
import static digit.config.ServiceConstants.ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_MESSAGE;
import static digit.config.ServiceConstants.ASSUMPTION_VALUE_NOT_FOUND_CODE;
import static digit.config.ServiceConstants.ASSUMPTION_VALUE_NOT_FOUND_MESSAGE;
import static digit.config.ServiceConstants.FILESTORE_ID_INVALID_CODE;
import static digit.config.ServiceConstants.FILESTORE_ID_INVALID_MESSAGE;
import static digit.config.ServiceConstants.INVALID_PLAN_CONFIG_ID_CODE;
import static digit.config.ServiceConstants.INVALID_PLAN_CONFIG_ID_MESSAGE;
import static digit.config.ServiceConstants.JSONPATH_ERROR_CODE;
import static digit.config.ServiceConstants.JSONPATH_ERROR_MESSAGE;
import static digit.config.ServiceConstants.MDMS_MASTER_ASSUMPTION;
import static digit.config.ServiceConstants.MDMS_MASTER_UPLOAD_CONFIGURATION;
import static digit.config.ServiceConstants.MDMS_PLAN_MODULE_NAME;
import static digit.config.ServiceConstants.SEARCH_CRITERIA_EMPTY_CODE;
import static digit.config.ServiceConstants.SEARCH_CRITERIA_EMPTY_MESSAGE;
import static digit.config.ServiceConstants.TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_CODE;
import static digit.config.ServiceConstants.TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_MESSAGE;
import static digit.config.ServiceConstants.TENANT_ID_EMPTY_CODE;
import static digit.config.ServiceConstants.TENANT_ID_EMPTY_MESSAGE;

@Component
@Slf4j
public class PlanConfigurationValidator {


    private MdmsUtil mdmsUtil;

    private PlanConfigurationRepository planConfigRepository;

    public PlanConfigurationValidator(MdmsUtil mdmsUtil, PlanConfigurationRepository planConfigRepository) {
        this.mdmsUtil = mdmsUtil;
        this.planConfigRepository = planConfigRepository;
    }

    /**
     * Validates the create request for plan configuration, including assumptions against MDMS data.
     * @param request The create request for plan configuration.
     */
    public void validateCreate(PlanConfigurationRequest request) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        String rootTenantId = planConfiguration.getTenantId().split("\\.")[0];
        Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(), rootTenantId);

        validateAssumptionKeyAgainstMDMS(request, mdmsData);
        validateAssumptionValue(planConfiguration);
        validateFilestoreId(planConfiguration);
        validateTemplateIdentifierAgainstMDMS(request, mdmsData);
    }

    /**
     * Validates the assumption values against the assumption keys in the plan configuration.
     * @param planConfiguration The plan configuration to validate.
     */
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

    /**
     * Validates the assumption keys against MDMS data.
     * @param request The request containing the plan configuration and the MDMS data.
     * @param mdmsData The MDMS data.
     */
    public void validateAssumptionKeyAgainstMDMS(PlanConfigurationRequest request, Object mdmsData) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        final String jsonPathForAssumption = "$." + MDMS_PLAN_MODULE_NAME + "." + MDMS_MASTER_ASSUMPTION + ".*";

        List<Object> assumptionListFromMDMS = null;
        try {
            log.info(jsonPathForAssumption);
            assumptionListFromMDMS = JsonPath.read(mdmsData, jsonPathForAssumption);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
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

    /**
     * Validates the file store IDs in the provided PlanConfiguration's Resource Mapping list.
     * @param planConfiguration The PlanConfiguration to validate.
     */
    public void validateFilestoreId(PlanConfiguration planConfiguration) {
        Set<String> fileStoreIds = planConfiguration.getFiles().stream()
                .map(File::getFilestoreId)
                .collect(Collectors.toSet());

        List<ResourceMapping> resourceMappingList = planConfiguration.getResourceMapping();
        for (ResourceMapping mapping : resourceMappingList) {
            if (!fileStoreIds.contains(mapping.getFilestoreId())) {
                log.error("Resource Mapping " + mapping.getMappedTo() + " does not have valid fileStoreId " + mapping.getFilestoreId());
                throw new CustomException(FILESTORE_ID_INVALID_CODE, FILESTORE_ID_INVALID_MESSAGE);
            }
        }
    }

    /**
     * Validates the template identifiers of files in the PlanConfigurationRequest against the list of template identifiers
     * obtained from MDMS (Master Data Management System) data.
     *
     * @param request   The PlanConfigurationRequest containing the PlanConfiguration to validate.
     * @param mdmsData  The MDMS data containing template identifiers to validate against.
     */
    public void validateTemplateIdentifierAgainstMDMS(PlanConfigurationRequest request, Object mdmsData) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        final String jsonPathForTemplateIdentifier = "$." + MDMS_PLAN_MODULE_NAME + "." + MDMS_MASTER_UPLOAD_CONFIGURATION + ".*";

        List<Object> templateIdentifierListFromMDMS = null;
        try {
            log.info(jsonPathForTemplateIdentifier);
            templateIdentifierListFromMDMS = JsonPath.read(mdmsData, jsonPathForTemplateIdentifier);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }

        for(File file : planConfiguration.getFiles())
        {
            if(!templateIdentifierListFromMDMS.contains(file.getTemplateIdentifier()))
            {
                log.error("Template Identifier " + file.getTemplateIdentifier() + " is not present in MDMS");
                throw new CustomException(TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_CODE, TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_MESSAGE);
            }
        }
    }

    /**
     * Validates the search request for plan configurations.
     * @param planConfigurationSearchRequest The search request for plan configurations.
     */
    public void validateSearchRequest(PlanConfigurationSearchRequest planConfigurationSearchRequest) {
        validateSearchCriteria(planConfigurationSearchRequest);
    }

    private void validateSearchCriteria(PlanConfigurationSearchRequest planConfigurationSearchRequest) {
        if (Objects.isNull(planConfigurationSearchRequest.getPlanConfigurationSearchCriteria())) {
            throw new CustomException(SEARCH_CRITERIA_EMPTY_CODE, SEARCH_CRITERIA_EMPTY_MESSAGE);
        }

        if (StringUtils.isEmpty(planConfigurationSearchRequest.getPlanConfigurationSearchCriteria().getTenantId())) {
            throw new CustomException(TENANT_ID_EMPTY_CODE, TENANT_ID_EMPTY_MESSAGE);
        }
    }

    /**
     * Validates the update request for plan configuration, including assumptions against MDMS data.
     * @param request The update request for plan configuration.
     */
    public void validateUpdateRequest(PlanConfigurationRequest request) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        String rootTenantId = planConfiguration.getTenantId().split("\\.")[0];
        Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(), rootTenantId);

        // Validate plan existence
        validatePlanConfigExistence(request);

        //Validate Assumption keys against MDMS data
        validateAssumptionKeyAgainstMDMS(request, mdmsData);

        //Validate Assumption values under operations with assumption keys
        validateAssumptionValue(planConfiguration);

        //Validate Resource Mapping's Filestore Ids
        validateFilestoreId(planConfiguration);

        //validate Template Identifier against MDMS data
        validateTemplateIdentifierAgainstMDMS(request, mdmsData);

    }

    /**
     * Validates the existence of the plan configuration in the repository.
     * @param request The request containing the plan configuration to validate.
     */
    public void validatePlanConfigExistence(PlanConfigurationRequest request) {
        // If plan id provided is invalid, throw an exception
        if(CollectionUtils.isEmpty(planConfigRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(request.getPlanConfiguration().getId())
                .build()))) {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
        }
    }
}
