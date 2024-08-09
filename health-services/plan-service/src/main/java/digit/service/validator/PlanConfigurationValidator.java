package digit.service.validator;

import com.jayway.jsonpath.JsonPath;
import digit.config.ServiceConstants;
import digit.repository.PlanConfigurationRepository;
import digit.util.MdmsUtil;
import digit.web.models.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import static digit.config.ServiceConstants.*;

@Component
@Slf4j
public class PlanConfigurationValidator {


    private MdmsUtil mdmsUtil;

    private PlanConfigurationRepository planConfigRepository;

    private MultiStateInstanceUtil centralInstanceUtil;

    public PlanConfigurationValidator(MdmsUtil mdmsUtil, PlanConfigurationRepository planConfigRepository, MultiStateInstanceUtil centralInstanceUtil) {
        this.mdmsUtil = mdmsUtil;
        this.planConfigRepository = planConfigRepository;
        this.centralInstanceUtil = centralInstanceUtil;
    }

    /**
     * Validates the create request for plan configuration, including assumptions against MDMS data.
     * @param request The create request for plan configuration.
     */
    public void validateCreate(PlanConfigurationRequest request) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(planConfiguration.getTenantId());
        Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(), rootTenantId);

        // Validate that the assumption keys in the request are present in the MDMS data
        validateAssumptionKeyAgainstMDMS(request, mdmsData);

        // Validate that the assumption values in the plan configuration are correct
        validateAssumptionValue(planConfiguration);

        // Validate the filestore ID in the plan configuration's request mappings
        validateFilestoreId(planConfiguration);

        // Validate that the template identifiers in the request match those in the MDMS data
        validateTemplateIdentifierAgainstMDMS(request, mdmsData);

        // Validate that the inputs for operations in the request match those in the MDMS data
        validateOperationsInputAgainstMDMS(request, mdmsData);

        // Validate that the resource mappings in the request match those in the MDMS data
        validateResourceMappingAgainstMDMS(request, mdmsData);

        // Validate the uniqueness of the 'mappedTo' fields in the resource mappings
        validateMappedToUniqueness(planConfiguration.getResourceMapping());

        // Validate the user information in the request
        validateUserInfo(request);

    }

    /**
     * Validates the assumption values against the assumption keys in the plan configuration.
     * If an operation uses an inactive assumption, throws an exception.
     *
     * @param planConfiguration The plan configuration to validate.
     */
    public void validateAssumptionValue(PlanConfiguration planConfiguration) {
        // Collect all active assumption keys
        Set<String> activeAssumptionKeys = planConfiguration.getAssumptions().stream()
                .filter(Assumption::getActive)
                .map(Assumption::getKey)
                .collect(Collectors.toSet());

        planConfiguration.getOperations().stream().filter(Operation::getActive) // Filter to only active operations
                .filter(operation -> !activeAssumptionKeys.contains(operation.getAssumptionValue())) // Check if assumption value is not in the active keys
                .forEach(operation -> {
                    log.error(ASSUMPTION_VALUE_NOT_FOUND_MESSAGE + operation.getAssumptionValue());
                    throw new CustomException(ASSUMPTION_VALUE_NOT_FOUND_CODE, ASSUMPTION_VALUE_NOT_FOUND_MESSAGE + " - " + operation.getAssumptionValue());
                });

    }


    /**
     * Validates the assumption keys against MDMS data.
     * @param request The request containing the plan configuration and the MDMS data.
     * @param mdmsData The MDMS data.
     */
    public void validateAssumptionKeyAgainstMDMS(PlanConfigurationRequest request, Object mdmsData) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        final String jsonPathForAssumption = "$." + MDMS_PLAN_MODULE_NAME + "." + MDMS_MASTER_ASSUMPTION + "[*].assumptions[*]";

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
                log.error(ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_MESSAGE + assumption.getKey());
                throw new CustomException(ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_CODE, ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_MESSAGE + " at JSONPath: " + jsonPathForAssumption);
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
        planConfiguration.getResourceMapping()
                .stream()
                .filter(resourceMapping -> !fileStoreIds.contains(resourceMapping.getFilestoreId()))
                .forEach(resourceMapping -> {
                    log.error( resourceMapping.getMappedTo() + FILESTORE_ID_INVALID_MESSAGE + resourceMapping.getFilestoreId());
                    throw new CustomException(FILESTORE_ID_INVALID_CODE, FILESTORE_ID_INVALID_MESSAGE);
                });

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
        final String jsonPathForTemplateIdentifier = "$." + MDMS_PLAN_MODULE_NAME + "." + MDMS_MASTER_UPLOAD_CONFIGURATION + ".*.id";
        final String jsonPathForTemplateIdentifierIsRequired = "$." + MDMS_PLAN_MODULE_NAME + "." + MDMS_MASTER_UPLOAD_CONFIGURATION + "[?(@.required == true)].id";

        List<Object> templateIdentifierListFromMDMS = null;
        List<Object> requiredTemplateIdentifierFromMDMS = null;
        Set<String> activeRequiredTemplates = new HashSet<>();

        try {
            log.info(jsonPathForTemplateIdentifier);
            templateIdentifierListFromMDMS = JsonPath.read(mdmsData, jsonPathForTemplateIdentifier);
            requiredTemplateIdentifierFromMDMS = JsonPath.read(mdmsData, jsonPathForTemplateIdentifierIsRequired);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }

        for(File file : planConfiguration.getFiles())
        {
            if(!templateIdentifierListFromMDMS.contains(file.getTemplateIdentifier()))
            {
                log.error(TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_MESSAGE + file.getTemplateIdentifier());
                throw new CustomException(TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_CODE, TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_MESSAGE);
            }

            if (file.getActive()) { // Check if the file is active
                String templateIdentifier = file.getTemplateIdentifier();
                if (requiredTemplateIdentifierFromMDMS.contains(templateIdentifier)) { // Check if the template identifier is required
                    if (!activeRequiredTemplates.add(templateIdentifier)) { // Ensure only one active file per required template identifier
                        log.error(ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER_MESSAGE + file.getTemplateIdentifier());
                        throw new CustomException(ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER_CODE, ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER_MESSAGE);
                    }
                }
            }
        }

        // Ensure at least one active file for each required template identifier
        requiredTemplateIdentifierFromMDMS
                .stream()
                .filter(requiredTemplate -> !activeRequiredTemplates.contains(requiredTemplate))
                .forEach(requiredTemplate -> {
                    log.error(REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND_MESSAGE + requiredTemplate);
                    throw new CustomException(REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND_CODE, REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND_MESSAGE);
                });

    }


    /**
     * Validates the operations input against the Master Data Management System (MDMS) data.
     *
     * @param request  The PlanConfigurationRequest containing the plan configuration and other details.
     * @param mdmsData The MDMS data containing the master rule configure inputs.
     */
    public void validateOperationsInputAgainstMDMS(PlanConfigurationRequest request, Object mdmsData) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        List<File> files = planConfiguration.getFiles();
        List<String> templateIds = files.stream()
                .map(File::getTemplateIdentifier)
                .collect(Collectors.toList());
        List<String> inputFileTypes = files.stream()
                .map(File::getInputFileType)
                .map(File.InputFileTypeEnum::toString)
                .collect(Collectors.toList());

        final String jsonPathForRuleInputs = "$." + MDMS_PLAN_MODULE_NAME + "." + MDMS_MASTER_SCHEMAS;
        List<Object> ruleInputsListFromMDMS = null;
        try {
            log.info(jsonPathForRuleInputs);
            ruleInputsListFromMDMS = JsonPath.read(mdmsData, jsonPathForRuleInputs);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }
        List<String> allowedColumns = getColumnsFromSchemaThatAreRuleInputs(ruleInputsListFromMDMS, templateIds, inputFileTypes);
        planConfiguration.getOperations().stream()
                .map(Operation::getOutput)
                .forEach(allowedColumns::add);

        planConfiguration.getOperations().stream()
                .filter(operation -> !allowedColumns.contains(operation.getInput()))
                .forEach(operation -> {
                    log.error(INPUT_KEY_NOT_FOUND_MESSAGE + operation.getInput());
                    throw new CustomException(INPUT_KEY_NOT_FOUND_CODE, INPUT_KEY_NOT_FOUND_MESSAGE);
                });

    }

    /**
     * Filters the Schema MDMS data by type and section
     * returns the list of columns which have the property 'isRuleConfigureInputs' as true
     *
     * @param schemas  List of schemas from MDMS
     * @param templateIds The list of template identifiers from request object
     * @param inputFileTypes The list of input file type from request object
     */
    public static List<String> getColumnsFromSchemaThatAreRuleInputs(List<Object> schemas, List<String> templateIds, List<String> inputFileTypes) {
        if (schemas == null) {
            return new ArrayList<>();
        }
        Set<String> finalData = new HashSet<>();
        for (Object item : schemas) {
            LinkedHashMap schemaEntity = (LinkedHashMap) item;
            if(!templateIds.contains(schemaEntity.get(MDMS_SCHEMA_SECTION)) || !inputFileTypes.contains(schemaEntity.get(MDMS_SCHEMA_TYPE))) continue;
            LinkedHashMap<String , LinkedHashMap> columns = (LinkedHashMap<String, LinkedHashMap>)((LinkedHashMap<String, LinkedHashMap>) schemaEntity.get(MDMS_SCHEMA_SCHEMA)).get(MDMS_SCHEMA_PROPERTIES);
            if(columns == null) return new ArrayList<>();
            columns.entrySet().stream()
                    .filter(column -> (boolean) column.getValue().get(MDMS_SCHEMA_PROPERTIES_IS_RULE_CONFIGURE_INPUT)) // Check if the value is true
                    .map(Map.Entry::getKey) // Extract the keys for matching entries
                    .forEach(finalData::add); // Add the keys to finalData

        }
        return new ArrayList<>(finalData);
    }


    /**
     * Validates that the 'mappedTo' values in the list of 'resourceMappings' are unique.
     * If a duplicate 'mappedTo' value is found, it logs an error and throws a CustomException.
     *
     * @param resourceMappings The list of 'ResourceMapping' objects to validate.
     * @throws CustomException If a duplicate 'mappedTo' value is found.
     */
    public static void validateMappedToUniqueness(List<ResourceMapping> resourceMappings) {
        Set<String> uniqueMappedToSet = new HashSet<>();
        resourceMappings.stream()
                .filter(mapping -> !uniqueMappedToSet.add(mapping.getFilestoreId() + "-" + mapping.getMappedTo()))
                .forEach(mapping -> {
                    log.error(DUPLICATE_MAPPED_TO_VALIDATION_ERROR_MESSAGE + mapping.getMappedTo() + " for FilestoreId " + mapping.getFilestoreId());
                    throw new CustomException(DUPLICATE_MAPPED_TO_VALIDATION_ERROR_CODE, DUPLICATE_MAPPED_TO_VALIDATION_ERROR_MESSAGE + " - " + mapping.getMappedTo() + " for FilestoreId " + mapping.getFilestoreId());
                });
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
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(planConfiguration.getTenantId());
        Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(), rootTenantId);

        // Validate the existence of the plan configuration in the request
        validatePlanConfigExistence(request);

        // Validate that the assumption keys in the request are present in the MDMS data
        validateAssumptionKeyAgainstMDMS(request, mdmsData);

        // Validate that the assumption values in the plan configuration are correct
        validateAssumptionValue(planConfiguration);

        // Validate the filestore ID in the plan configuration's request mappings
        validateFilestoreId(planConfiguration);

        // Validate that the template identifiers in the request match those in the MDMS data
        validateTemplateIdentifierAgainstMDMS(request, mdmsData);

        // Validate that the inputs for operations in the request match those in the MDMS data
        validateOperationsInputAgainstMDMS(request, mdmsData);

        // Validate the dependencies between operations in the plan configuration
        validateOperationDependencies(planConfiguration);

        // Validate that the resource mappings in the request match those in the MDMS data
        validateResourceMappingAgainstMDMS(request, mdmsData);

        // Validate the uniqueness of the 'mappedTo' fields in the resource mappings
        validateMappedToUniqueness(planConfiguration.getResourceMapping());

        // Validate the user information in the request
        validateUserInfo(request);

    }

    /**
     * Validates the existence of the plan configuration in the repository.
     * @param request The request containing the plan configuration to validate.
     */
    public PlanConfiguration validatePlanConfigExistence(PlanConfigurationRequest request) {
        // If plan id provided is invalid, throw an exception
        List<PlanConfiguration> planConfigurationList = planConfigRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(request.getPlanConfiguration().getId())
                .build());

        if(CollectionUtils.isEmpty(planConfigurationList)) {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
        }

        return planConfigurationList.get(0);
    }

    /**
     * Validates that if an operation is inactive, its output is not used as input in any other active operation.
     * If the condition is violated, it logs an error and throws a CustomException.
     *
     * @param planConfiguration The plan configuration to validate.
     * @throws CustomException If an inactive operation's output is used as input in any other active operation.
     */
    public static void validateOperationDependencies(PlanConfiguration planConfiguration) {
        // Collect all active operations' inputs
        Set<String> activeInputs = planConfiguration.getOperations().stream()
                .filter(Operation::getActive)
                .map(Operation::getInput)
                .collect(Collectors.toSet());

        // Check for each inactive operation
        planConfiguration.getOperations().stream()
                .filter(operation -> !operation.getActive() && activeInputs.contains(operation.getOutput()))
                .forEach(operation -> {
                    log.error(INACTIVE_OPERATION_USED_AS_INPUT_MESSAGE + operation.getOutput());
                    throw new CustomException(INACTIVE_OPERATION_USED_AS_INPUT_CODE, INACTIVE_OPERATION_USED_AS_INPUT_MESSAGE + operation.getOutput());
                });
    }


	/**
	 * Validate input (BCode) against MDMS data.
	 * @param request plan configauration request.
	 * @param mdmsData MDMS data object.
	 */
    public void validateResourceMappingAgainstMDMS(PlanConfigurationRequest request, Object mdmsData) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        List<File> files = planConfiguration.getFiles();
        List<String> templateIds = files.stream()
                .map(File::getTemplateIdentifier)
                .toList();
        List<String> inputFileTypes = files.stream()
                .map(File::getInputFileType)
                .map(File.InputFileTypeEnum::toString)
                .toList();

        final String jsonPathForRuleInputs = "$." + MDMS_PLAN_MODULE_NAME + "." + MDMS_MASTER_SCHEMAS;
        List<Object> ruleInputsListFromMDMS = null;
        try {
            log.info(jsonPathForRuleInputs);
            ruleInputsListFromMDMS = JsonPath.read(mdmsData, jsonPathForRuleInputs);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }
        List<String> requiredColumns = getRequiredColumnsFromSchema(ruleInputsListFromMDMS, templateIds, inputFileTypes);
        List<ResourceMapping> resourceMappings =  planConfiguration.getResourceMapping();

        // Throw a custom exception if no active mappings with BOUNDARY_CODE are found
        if(requiredColumns.contains(ServiceConstants.BOUNDARY_CODE)) {
            boolean exists = resourceMappings.stream()
                    .anyMatch(mapping -> mapping.getActive() && mapping.getMappedTo().equals(ServiceConstants.BOUNDARY_CODE));

            if (!exists) {
                throw new CustomException(BOUNDARY_CODE_MAPPING_NOT_FOUND_CODE, BOUNDARY_CODE_MAPPING_NOT_FOUND_MESSAGE);
            }
        }
        }


    /**
     * Filters the Schema MDMS data by type and section
     * returns the list of columns which have the property 'isRequired' as true
     *
     * @param schemas  List of schemas from MDMS
     * @param templateIds The list of template identifiers from request object
     * @param inputFileTypes The list of input file type from request object
     * @return List of Columns that are required
     */
    public static List<String> getRequiredColumnsFromSchema(List<Object> schemas, List<String> templateIds, List<String> inputFileTypes) {
        if (schemas == null) {
            return new ArrayList<>();
        }
        Set<String> finalData = new HashSet<>();
        for (Object item : schemas) {
            LinkedHashMap<?, ?> schemaEntity = (LinkedHashMap) item;
            if(!templateIds.contains(schemaEntity.get(MDMS_SCHEMA_SECTION)) || !inputFileTypes.contains(schemaEntity.get(MDMS_SCHEMA_TYPE))) continue;
            LinkedHashMap<String , LinkedHashMap> columns = (LinkedHashMap<String, LinkedHashMap>)((LinkedHashMap<String, LinkedHashMap>) schemaEntity.get(MDMS_SCHEMA_SCHEMA)).get(MDMS_SCHEMA_PROPERTIES);
            if(columns == null) return new ArrayList<>();
            columns.entrySet().stream()
                    .filter(column -> (boolean) column.getValue().get(MDMS_SCHEMA_PROPERTIES_IS_REQUIRED)) // Check if the value is true
                    .map(Map.Entry::getKey) // Extract the keys for matching entries
                    .forEach(finalData::add); // Add the keys to finalData
        }
        return new ArrayList<>(finalData);
    }

    /**
     * Validates the user information within the provided PlanConfigurationRequest.
     *
     * @param request the PlanConfigurationRequest containing the user information to be validated
     * @throws CustomException if the user information is missing in the request
     */
    public void validateUserInfo(PlanConfigurationRequest request)
    {
        if (ObjectUtils.isEmpty(request.getRequestInfo().getUserInfo())) {
            log.error(USERINFO_MISSING_MESSAGE);
            throw new CustomException(USERINFO_MISSING_CODE, USERINFO_MISSING_MESSAGE);
        }
    }
}
