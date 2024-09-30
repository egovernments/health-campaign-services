package digit.service.validator;

import com.jayway.jsonpath.JsonPath;
import digit.config.ServiceConstants;
import digit.repository.PlanConfigurationRepository;
import digit.util.MdmsUtil;
import digit.util.MdmsV2Util;
import digit.util.CommonUtil;
import digit.web.models.*;

import java.util.*;
import java.util.stream.Collectors;

import digit.web.models.mdmsV2.Mdms;
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

    private MdmsV2Util mdmsV2Util;

    private PlanConfigurationRepository planConfigRepository;

    private CommonUtil commonUtil;

    private MultiStateInstanceUtil centralInstanceUtil;

    public PlanConfigurationValidator(MdmsUtil mdmsUtil, MdmsV2Util mdmsV2Util, PlanConfigurationRepository planConfigRepository, CommonUtil commonUtil, MultiStateInstanceUtil centralInstanceUtil) {
        this.mdmsUtil = mdmsUtil;
        this.mdmsV2Util = mdmsV2Util;
        this.planConfigRepository = planConfigRepository;
        this.commonUtil = commonUtil;
        this.centralInstanceUtil = centralInstanceUtil;
    }

    /**
     * Validates the create request for plan configuration, including assumptions against MDMS data.
     *
     * @param request The create request for plan configuration.
     */
    public void validateCreate(PlanConfigurationRequest request) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(planConfiguration.getTenantId());
        Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(), rootTenantId);
        List<Mdms> mdmsV2Data = mdmsV2Util.fetchMdmsV2Data(request.getRequestInfo(), rootTenantId, MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_SCHEMA_VEHICLE_DETAILS);

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

        //Validating plan config name against MDMS data
        validatePlanConfigName(request, mdmsData);

        // Validate the user information in the request
        validateUserInfo(request);

        // Validates the vehicle id from additional details object against the data from mdms v2
        validateVehicleIdsFromAdditionalDetailsAgainstMDMS(request, mdmsV2Data);

    }

    /**
     * Validates the name of the plan configuration against a regex pattern retrieved from MDMS data.
     *
     * @param request  the plan configuration request containing the plan configuration details
     * @param mdmsData the MDMS data containing the name validation regex patterns
     * @throws CustomException if the JSONPath evaluation fails, the name validation list from MDMS is empty,
     *                         or the plan configuration name validation fails.
     */
    public void validatePlanConfigName(PlanConfigurationRequest request, Object mdmsData) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();

        final String jsonPathForNameValidation = JSON_ROOT_PATH + MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_NAME_VALIDATION + "[*].data";
        List<Object> nameValidationListFromMDMS = null;
        try {
            nameValidationListFromMDMS = JsonPath.read(mdmsData, jsonPathForNameValidation);
        } catch (Exception e) {
            log.error(jsonPathForNameValidation);
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }

        if (CollectionUtils.isEmpty(nameValidationListFromMDMS)) {
            throw new CustomException(NAME_VALIDATION_LIST_EMPTY_CODE, NAME_VALIDATION_LIST_EMPTY_MESSAGE);
        }

        String regexPattern = (String) nameValidationListFromMDMS.get(0);
        if (!commonUtil.validateStringAgainstRegex(regexPattern, planConfiguration.getName())) {
            throw new CustomException(NAME_VALIDATION_FAILED_CODE, NAME_VALIDATION_FAILED_MESSAGE);
        }

    }


    /**
     * Validates the assumption values against the assumption keys in the plan configuration.
     * If an operation uses an inactive assumption, throws an exception.
     *
     * @param planConfiguration The plan configuration to validate.
     */
    public void validateAssumptionValue(PlanConfiguration planConfiguration) {
        if (isSetupCompleted(planConfiguration)) {
            checkForEmptyAssumption(planConfiguration);
            checkForEmptyOperation(planConfiguration);

            // Collect all active assumption keys
            Set<String> activeAssumptionKeys = planConfiguration.getAssumptions().stream()
                    .filter(Assumption::getActive)
                    .map(Assumption::getKey)
                    .collect(Collectors.toSet());

            planConfiguration.getOperations().stream()
                    .filter(Operation::getActive)
                    .forEach(operation -> {
                        if (!activeAssumptionKeys.contains(operation.getAssumptionValue())) {
                            log.error("Assumption Value " + operation.getAssumptionValue() + " is not present in the list of active Assumption Keys");
                            throw new CustomException(ASSUMPTION_VALUE_NOT_FOUND_CODE, ASSUMPTION_VALUE_NOT_FOUND_MESSAGE + " - " + operation.getAssumptionValue());
                        }
                    });

        }
    }


    /**
     * Validates the assumption keys against MDMS data.
     *
     * @param request  The request containing the plan configuration and the MDMS data.
     * @param mdmsData The MDMS data.
     */
    public void validateAssumptionKeyAgainstMDMS(PlanConfigurationRequest request, Object mdmsData) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        if (!CollectionUtils.isEmpty(planConfiguration.getAssumptions())) {
            final String jsonPathForAssumption = JSON_ROOT_PATH + MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_ASSUMPTION + FILTER_ALL_ASSUMPTIONS;

            List<Object> assumptionListFromMDMS = null;
            try {
                log.info(jsonPathForAssumption);
                assumptionListFromMDMS = JsonPath.read(mdmsData, jsonPathForAssumption);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
            }

            HashSet<Object> assumptionSetFromMDMS = new HashSet<>(assumptionListFromMDMS);
            planConfiguration.getAssumptions().forEach(assumption -> {
                        if (!assumptionSetFromMDMS.contains(assumption.getKey())) {
                            log.error(ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_MESSAGE + assumption.getKey());
                            throw new CustomException(ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_CODE, ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_MESSAGE + " at JSONPath: " + jsonPathForAssumption);
                        }
                    }
            );

        }
    }

    /**
     * Validates the file store IDs in the provided PlanConfiguration's Resource Mapping list.
     *
     * @param planConfiguration The PlanConfiguration to validate.
     */
    public void validateFilestoreId(PlanConfiguration planConfiguration) {
        if (isSetupCompleted(planConfiguration)) {
            checkForEmptyFiles(planConfiguration);
            checkForEmptyResourceMapping(planConfiguration);

            Set<String> fileStoreIds = planConfiguration.getFiles().stream()
                    .map(File::getFilestoreId)
                    .collect(Collectors.toSet());

            planConfiguration.getResourceMapping().stream().forEach(mapping -> {
                if (!fileStoreIds.contains(mapping.getFilestoreId())) {
                    log.error("Resource Mapping " + mapping.getMappedTo() + " does not have valid fileStoreId " + mapping.getFilestoreId());
                    throw new CustomException(FILESTORE_ID_INVALID_CODE, FILESTORE_ID_INVALID_MESSAGE);
                }
            });

        }
    }

    /**
     * Validates the template identifiers of files in the PlanConfigurationRequest against the list of template identifiers
     * obtained from MDMS (Master Data Management System) data.
     *
     * @param request  The PlanConfigurationRequest containing the PlanConfiguration to validate.
     * @param mdmsData The MDMS data containing template identifiers to validate against.
     */
    public void validateTemplateIdentifierAgainstMDMS(PlanConfigurationRequest request, Object mdmsData) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        if (!CollectionUtils.isEmpty(planConfiguration.getFiles())) {
            final String jsonPathForTemplateIdentifier = JSON_ROOT_PATH + MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_UPLOAD_CONFIGURATION + ".*.id";
            final String jsonPathForTemplateIdentifierIsRequired = JSON_ROOT_PATH + MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_UPLOAD_CONFIGURATION + "[?(@.required == true)].id";

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

            HashSet<Object> templateIdentifierSetFromMDMS = new HashSet<>(templateIdentifierListFromMDMS);
            HashSet<Object> requiredTemplateIdentifierSetFromMDMS = new HashSet<>(requiredTemplateIdentifierFromMDMS);

            for (File file : planConfiguration.getFiles()) {
                if (!templateIdentifierSetFromMDMS.contains(file.getTemplateIdentifier())) {
                    log.error(TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_MESSAGE + file.getTemplateIdentifier());
                    throw new CustomException(TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_CODE, TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_MESSAGE);
                }

                if (file.getActive()) { // Check if the file is active
                    String templateIdentifier = file.getTemplateIdentifier();
                    if (requiredTemplateIdentifierSetFromMDMS.contains(templateIdentifier)) { // Check if the template identifier is required
                        if (!activeRequiredTemplates.add(templateIdentifier)) { // Ensure only one active file per required template identifier
                            log.error(ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER_MESSAGE + file.getTemplateIdentifier());
                            throw new CustomException(ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER_CODE, ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER_MESSAGE);
                        }
                    }
                }
            }

            // Ensure at least one active file for each required template identifier
            requiredTemplateIdentifierSetFromMDMS
                    .stream()
                    .forEach(requiredTemplate -> {
                        if (!activeRequiredTemplates.contains(requiredTemplate)) {
                            log.error("Required Template Identifier " + requiredTemplate + " does not have any active file.");
                            throw new CustomException(REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND_CODE, REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND_MESSAGE);
                        }
                    });

        }
    }


    /**
     * Validates the operations input against the Master Data Management System (MDMS) data.
     *
     * @param request  The PlanConfigurationRequest containing the plan configuration and other details.
     * @param mdmsData The MDMS data containing the master rule configure inputs.
     */
    public void validateOperationsInputAgainstMDMS(PlanConfigurationRequest request, Object mdmsData) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();

        if (isSetupCompleted(planConfiguration)) {
            checkForEmptyFiles(planConfiguration);
            checkForEmptyOperation(planConfiguration);

            List<File> files = planConfiguration.getFiles();
            List<String> templateIds = files.stream()
                    .map(File::getTemplateIdentifier)
                    .collect(Collectors.toList());
            List<String> inputFileTypes = files.stream()
                    .map(File::getInputFileType)
                    .map(File.InputFileTypeEnum::toString)
                    .collect(Collectors.toList());

            final String jsonPathForRuleInputs = JSON_ROOT_PATH + MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_SCHEMAS;
            List<Object> ruleInputsListFromMDMS = null;
            try {
                log.info(jsonPathForRuleInputs);
                ruleInputsListFromMDMS = JsonPath.read(mdmsData, jsonPathForRuleInputs);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
            }

            HashSet<Object> ruleInputsSetFromMDMS = new HashSet<>(ruleInputsListFromMDMS);

            HashSet<String> allowedColumns = getColumnsFromSchemaThatAreRuleInputs(ruleInputsSetFromMDMS, templateIds, inputFileTypes);
            planConfiguration.getOperations().stream()
                    .map(Operation::getOutput)
                    .forEach(allowedColumns::add);

            planConfiguration.getOperations().stream().forEach(operation -> {
                if (!allowedColumns.contains(operation.getInput())) {
                    log.error("Input Value " + operation.getInput() + " is not present in MDMS Input List");
                    throw new CustomException(INPUT_KEY_NOT_FOUND_CODE, INPUT_KEY_NOT_FOUND_MESSAGE);
                }
            });

        }
    }

    /**
     * Filters the Schema MDMS data by type and section
     * returns the list of columns which have the property 'isRuleConfigureInputs' as true
     *
     * @param schemas        List of schemas from MDMS
     * @param templateIds    The list of template identifiers from request object
     * @param inputFileTypes The list of input file type from request object
     */
    public static HashSet<String> getColumnsFromSchemaThatAreRuleInputs
    (HashSet<Object> schemas, List<String> templateIds, List<String> inputFileTypes) {
        if (schemas == null) {
            return new HashSet<>();
        }
        HashSet<String> finalData = new HashSet<>();
        for (Object item : schemas) {
            LinkedHashMap schemaEntity = (LinkedHashMap) item;
            if (!templateIds.contains(schemaEntity.get(MDMS_SCHEMA_SECTION)) || !inputFileTypes.contains(schemaEntity.get(MDMS_SCHEMA_TYPE)))
                continue;
            LinkedHashMap<String, LinkedHashMap> columns = (LinkedHashMap<String, LinkedHashMap>) ((LinkedHashMap<String, LinkedHashMap>) schemaEntity.get(MDMS_SCHEMA_SCHEMA)).get(MDMS_SCHEMA_PROPERTIES);
            if (columns == null) return new HashSet<>();
            columns.entrySet().stream()
                    .forEach(column -> {
                        LinkedHashMap<String, Boolean> data = column.getValue();
                        if (data.get(MDMS_SCHEMA_PROPERTIES_IS_RULE_CONFIGURE_INPUT)) {
                            finalData.add(column.getKey());
                        }
                    });   // Add the keys to finalData
        }
        return finalData;
    }


    /**
     * Validates that the 'mappedTo' values in the list of 'resourceMappings' are unique.
     * If a duplicate 'mappedTo' value is found, it logs an error and throws a CustomException.
     *
     * @param resourceMappings The list of 'ResourceMapping' objects to validate.
     * @throws CustomException If a duplicate 'mappedTo' value is found.
     */
    public static void validateMappedToUniqueness(List<ResourceMapping> resourceMappings) {
        if (!CollectionUtils.isEmpty(resourceMappings)) {
            Set<String> uniqueMappedToSet = new HashSet<>();
            resourceMappings.stream().forEach(mapping -> {
                String uniqueKey = mapping.getFilestoreId() + "-" + mapping.getMappedTo();
                if (!uniqueMappedToSet.add(uniqueKey)) {
                    log.error("Duplicate MappedTo " + mapping.getMappedTo() + " for FilestoreId " + mapping.getFilestoreId());
                    throw new CustomException(DUPLICATE_MAPPED_TO_VALIDATION_ERROR_CODE, DUPLICATE_MAPPED_TO_VALIDATION_ERROR_MESSAGE + " - " + mapping.getMappedTo() + " for FilestoreId " + mapping.getFilestoreId());
                }
            });
        }
    }


    /**
     * Validates the search request for plan configurations.
     *
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
     *
     * @param request The update request for plan configuration.
     */
    public void validateUpdateRequest(PlanConfigurationRequest request) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(planConfiguration.getTenantId());
        Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(), rootTenantId);
        List<Mdms> mdmsV2Data = mdmsV2Util.fetchMdmsV2Data(request.getRequestInfo(), rootTenantId, MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_SCHEMA_VEHICLE_DETAILS);

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

        //Validating plan config name against MDMS data
        validatePlanConfigName(request, mdmsData);

        // Validate the user information in the request
        validateUserInfo(request);

        // Validates the vehicle id from additional details object against the data from mdms v2
        validateVehicleIdsFromAdditionalDetailsAgainstMDMS(request, mdmsV2Data);
    }

    /**
     * Validates the existence of the plan configuration in the repository.
     *
     * @param request The request containing the plan configuration to validate.
     */
    public PlanConfiguration validatePlanConfigExistence(PlanConfigurationRequest request) {
        // If plan id provided is invalid, throw an exception
        List<PlanConfiguration> planConfigurationList = planConfigRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(request.getPlanConfiguration().getId())
                .build());

        if (CollectionUtils.isEmpty(planConfigurationList)) {
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
        if (!CollectionUtils.isEmpty(planConfiguration.getOperations())) {
            // Collect all active operations' inputs
            Set<String> activeInputs = planConfiguration.getOperations().stream()
                    .filter(Operation::getActive)
                    .map(Operation::getInput)
                    .collect(Collectors.toSet());

            // Check for each inactive operation
            planConfiguration.getOperations().stream().forEach(operation -> {
                if (!operation.getActive() && activeInputs.contains(operation.getOutput())) {
                    log.error(INACTIVE_OPERATION_USED_AS_INPUT_MESSAGE + operation.getOutput());
                    throw new CustomException(INACTIVE_OPERATION_USED_AS_INPUT_CODE, INACTIVE_OPERATION_USED_AS_INPUT_MESSAGE + operation.getOutput());
                }
            });
        }
    }


    /**
     * Validate input (BCode) against MDMS data.
     *
     * @param request  plan configauration request.
     * @param mdmsData MDMS data object.
     */
    public void validateResourceMappingAgainstMDMS(PlanConfigurationRequest request, Object mdmsData) {
        PlanConfiguration planConfiguration = request.getPlanConfiguration();

        if (isSetupCompleted(planConfiguration)) {
            checkForEmptyFiles(planConfiguration);
            checkForEmptyResourceMapping(planConfiguration);

            List<File> files = planConfiguration.getFiles();
            List<String> templateIds = files.stream()
                    .map(File::getTemplateIdentifier)
                    .toList();
            List<String> inputFileTypes = files.stream()
                    .map(File::getInputFileType)
                    .map(File.InputFileTypeEnum::toString)
                    .toList();

            final String jsonPathForRuleInputs = JSON_ROOT_PATH + MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_SCHEMAS;
            List<Object> ruleInputsListFromMDMS = null;
            try {
                log.info(jsonPathForRuleInputs);
                ruleInputsListFromMDMS = JsonPath.read(mdmsData, jsonPathForRuleInputs);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
            }
            HashSet<Object> ruleInputsSetFromMDMS = new HashSet<>(ruleInputsListFromMDMS);
            HashSet<String> requiredColumns = getRequiredColumnsFromSchema(ruleInputsSetFromMDMS, templateIds, inputFileTypes);
            List<ResourceMapping> resourceMappings = planConfiguration.getResourceMapping();

            // Throw a custom exception if no active mappings with BOUNDARY_CODE are found
            if (requiredColumns.contains(ServiceConstants.BOUNDARY_CODE)) {
                boolean exists = resourceMappings.stream()
                        .anyMatch(mapping -> mapping.getActive() && mapping.getMappedTo().equals(ServiceConstants.BOUNDARY_CODE));

                if (!exists) {
                    throw new CustomException(BOUNDARY_CODE_MAPPING_NOT_FOUND_CODE, BOUNDARY_CODE_MAPPING_NOT_FOUND_MESSAGE);
                }
            }
        }

    }

    /**
     * Filters the Schema MDMS data by type and section
     * returns the list of columns which have the property 'isRequired' as true
     *
     * @param schemas        List of schemas from MDMS
     * @param templateIds    The list of template identifiers from request object
     * @param inputFileTypes The list of input file type from request object
     * @return List of Columns that are required
     */
    public static HashSet<String> getRequiredColumnsFromSchema
    (HashSet<Object> schemas, List<String> templateIds, List<String> inputFileTypes) {
        if (CollectionUtils.isEmpty(schemas)) {
            return new HashSet<>();
        }
        HashSet<String> finalData = new HashSet<>();
        for (Object item : schemas) {
            LinkedHashMap<?, ?> schemaEntity = (LinkedHashMap) item;
            if (!templateIds.contains(schemaEntity.get(MDMS_SCHEMA_SECTION)) || !inputFileTypes.contains(schemaEntity.get(MDMS_SCHEMA_TYPE)))
                continue;
            LinkedHashMap<String, LinkedHashMap> columns = (LinkedHashMap<String, LinkedHashMap>) ((LinkedHashMap<String, LinkedHashMap>) schemaEntity.get(MDMS_SCHEMA_SCHEMA)).get(MDMS_SCHEMA_PROPERTIES);
            if (columns == null) return new HashSet<>();
            columns.entrySet().stream().forEach(column -> {
                LinkedHashMap<String, Boolean> data = column.getValue();
                if (data.get(MDMS_SCHEMA_PROPERTIES_IS_REQUIRED)) {
                    finalData.add(column.getKey());
                }
            });
        }
        return finalData;
    }

    /**
     * Validates the user information within the provided PlanConfigurationRequest.
     *
     * @param request the PlanConfigurationRequest containing the user information to be validated
     * @throws CustomException if the user information is missing in the request
     */
    public void validateUserInfo(PlanConfigurationRequest request) {
        if (ObjectUtils.isEmpty(request.getRequestInfo().getUserInfo())) {
            log.error(USERINFO_MISSING_MESSAGE);
            throw new CustomException(USERINFO_MISSING_CODE, USERINFO_MISSING_MESSAGE);
        }
    }

    /**
     * Validates Vehicle ids from additional details against MDMS V2
     *
     * @param request    plan configuration request
     * @param mdmsV2Data mdms v2 data object
     */
    public void validateVehicleIdsFromAdditionalDetailsAgainstMDMS(PlanConfigurationRequest request, List<Mdms> mdmsV2Data) {
        List<String> vehicleIdsLinkedWithPlanConfig = commonUtil.extractVehicleIdsFromAdditionalDetails(request.getPlanConfiguration().getAdditionalDetails());

        if (!CollectionUtils.isEmpty(vehicleIdsLinkedWithPlanConfig)) {
            List<String> vehicleIdsFromMdms = mdmsV2Data.stream()
                    .map(Mdms::getId)
                    .collect(Collectors.toList());

            List<String> finalVehicleIdsFromMdms = vehicleIdsFromMdms;
            vehicleIdsLinkedWithPlanConfig.stream()
                    .forEach(vehicleId -> {
                        if (!finalVehicleIdsFromMdms.contains(vehicleId)) {
                            log.error("Vehicle Id " + vehicleId + " is not present in MDMS");
                            throw new CustomException(VEHICLE_ID_NOT_FOUND_IN_MDMS_CODE, VEHICLE_ID_NOT_FOUND_IN_MDMS_MESSAGE);
                        }
                    });
        }

    }


    public boolean isSetupCompleted(PlanConfiguration planConfiguration) {
        return planConfiguration.getStatus() == PlanConfiguration.StatusEnum.SETUP_COMPLETED;
    }

    // Checks for whether file, assumption, operation or resource mapping is empty or null at a certain status
    private void checkForEmptyFiles(PlanConfiguration planConfiguration) {
        if (CollectionUtils.isEmpty(planConfiguration.getFiles())) {
            log.error("Files cannot be empty at status = " + PlanConfiguration.StatusEnum.SETUP_COMPLETED);
            throw new CustomException(FILES_NOT_FOUND_CODE, FILES_NOT_FOUND_MESSAGE);
        }
    }

    private void checkForEmptyAssumption(PlanConfiguration planConfiguration) {
        if (CollectionUtils.isEmpty(planConfiguration.getAssumptions())) {
            log.error("Assumptions cannot be empty at status = " + PlanConfiguration.StatusEnum.SETUP_COMPLETED);
            throw new CustomException(ASSUMPTIONS_NOT_FOUND_CODE, ASSUMPTIONS_NOT_FOUND_MESSAGE);
        }
    }

    private void checkForEmptyOperation(PlanConfiguration planConfiguration) {
        if (CollectionUtils.isEmpty(planConfiguration.getOperations())) {
            log.error("Operations cannot be empty at status = " + PlanConfiguration.StatusEnum.SETUP_COMPLETED);
            throw new CustomException(OPERATIONS_NOT_FOUND_CODE, OPERATIONS_NOT_FOUND_MESSAGE);
        }
    }

    private void checkForEmptyResourceMapping(PlanConfiguration planConfiguration) {
        if (CollectionUtils.isEmpty(planConfiguration.getResourceMapping())) {
            log.error("Resource mapping cannot be empty at status = " + PlanConfiguration.StatusEnum.SETUP_COMPLETED);
            throw new CustomException(RESOURCE_MAPPING_NOT_FOUND_CODE, RESOURCE_MAPPING_NOT_FOUND_MESSAGE);
        }
    }

}
