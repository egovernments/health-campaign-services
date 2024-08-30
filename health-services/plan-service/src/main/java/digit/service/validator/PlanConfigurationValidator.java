package digit.service.validator;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static digit.config.ServiceConstants.*;

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
        validateOperationsInputAgainstMDMS(request, mdmsData);
        validateResourceMappingAgainstMDMS(request, mdmsData);
        validateMappedToUniqueness(planConfiguration.getResourceMapping());
        validateVehicleIdsFromAdditionalDetailsAgainstMDMS(request, mdmsData);
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

        List<Operation> operations = planConfiguration.getOperations();
        for (Operation operation : operations) {
            // Check if the operation is using an assumption key that is not in the set of active assumption keys
            if (operation.getActive() && !activeAssumptionKeys.contains(operation.getAssumptionValue())) {
                log.error("Assumption Value " + operation.getAssumptionValue() + " is not present in the list of active Assumption Keys");
                throw new CustomException(ASSUMPTION_VALUE_NOT_FOUND_CODE, ASSUMPTION_VALUE_NOT_FOUND_MESSAGE + " - " + operation.getAssumptionValue());
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
        final String jsonPathForAssumption = "$." + MDMS + MDMS_MASTER_ASSUMPTION + "[*].assumptions[*]";

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
                log.error("Template Identifier " + file.getTemplateIdentifier() + " is not present in MDMS");
                throw new CustomException(TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_CODE, TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_MESSAGE);
            }

            if (file.getActive()) { // Check if the file is active
                String templateIdentifier = file.getTemplateIdentifier();
                if (requiredTemplateIdentifierFromMDMS.contains(templateIdentifier)) { // Check if the template identifier is required
                    if (!activeRequiredTemplates.add(templateIdentifier)) { // Ensure only one active file per required template identifier
                        log.error("Only one file with the required Template Identifier should be present " + file.getTemplateIdentifier());
                        throw new CustomException(ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER_CODE, ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER_MESSAGE);
                    }
                }
            }
        }

        // Ensure at least one active file for each required template identifier
        for (Object requiredTemplate : requiredTemplateIdentifierFromMDMS) {
            if (!activeRequiredTemplates.contains(requiredTemplate)) {
                log.error("Required Template Identifier " + requiredTemplate + " does not have any active file.");
                throw new CustomException(REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND_CODE, REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND_MESSAGE);
            }
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
        List<String> allowedColumns = getRuleConfigInputsFromSchema(ruleInputsListFromMDMS, templateIds, inputFileTypes);
        planConfiguration.getOperations().stream()
                .map(Operation::getOutput)
                .forEach(allowedColumns::add);
        for (Operation operation : planConfiguration.getOperations()) {
            if (!allowedColumns.contains(operation.getInput())) {
                log.error("Input Value " + operation.getInput() + " is not present in MDMS Input List");
                throw new CustomException(INPUT_KEY_NOT_FOUND_CODE, INPUT_KEY_NOT_FOUND_MESSAGE);
            }
        }
    }

    // helper function
    public static List<String> getRuleConfigInputsFromSchema(List<Object> schemas, List<String> templateIds, List<String> inputFileTypes) {
        if (schemas == null) {
            return new ArrayList<>();
        }
        Set<String> finalData = new HashSet<>();
        for (Object item : schemas) {
            LinkedHashMap schemaEntity = (LinkedHashMap) item;
            if(!templateIds.contains(schemaEntity.get(MDMS_SCHEMA_SECTION)) || !inputFileTypes.contains(schemaEntity.get(MDMS_SCHEMA_TYPE))) continue;
            LinkedHashMap<String , LinkedHashMap> columns = (LinkedHashMap<String, LinkedHashMap>)((LinkedHashMap<String, LinkedHashMap>) schemaEntity.get(MDMS_SCHEMA_SCHEMA)).get(MDMS_SCHEMA_PROPERTIES);
            if(columns == null) return new ArrayList<>();
            for(Map.Entry<String, LinkedHashMap> column : columns.entrySet()){
                LinkedHashMap<String, Boolean> data = column.getValue();
                if(data.get(MDMS_SCHEMA_PROPERTIES_IS_RULE_CONFIGURE_INPUT)){
                    finalData.add(column.getKey());
                }
            }
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
        for (ResourceMapping mapping : resourceMappings) {
            String uniqueKey = mapping.getFilestoreId() + "-" + mapping.getMappedTo();
            if (!uniqueMappedToSet.add(uniqueKey)) {
                log.error("Duplicate MappedTo " + mapping.getMappedTo() + " for FilestoreId " + mapping.getFilestoreId());
                throw new CustomException(DUPLICATE_MAPPED_TO_VALIDATION_ERROR_CODE,
                        DUPLICATE_MAPPED_TO_VALIDATION_ERROR_MESSAGE + " - " + mapping.getMappedTo() + " for FilestoreId " + mapping.getFilestoreId());
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
        PlanConfiguration planConfigurationFromDB = validatePlanConfigExistence(request);

        validateAssumptionKeyAgainstMDMS(request, mdmsData);
        validateAssumptionValue(planConfiguration);
        validateFilestoreId(planConfiguration);
//        validateFilesActive(planConfigurationFromDB, planConfiguration);
        validateTemplateIdentifierAgainstMDMS(request, mdmsData);
        validateOperationsInputAgainstMDMS(request, mdmsData);
        validateOperationDependencies(planConfiguration);
        validateResourceMappingAgainstMDMS(request, mdmsData);
        validateMappedToUniqueness(planConfiguration.getResourceMapping());

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
        for (Operation operation : planConfiguration.getOperations()) {
            if (!operation.getActive() && activeInputs.contains(operation.getOutput())) {
                log.error(INACTIVE_OPERATION_USED_AS_INPUT_MESSAGE + operation.getOutput());
                throw new CustomException(INACTIVE_OPERATION_USED_AS_INPUT_CODE, INACTIVE_OPERATION_USED_AS_INPUT_MESSAGE + operation.getOutput());
            }
        }
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
        List<String> requiredColumns = getIsTruePropertyFromSchema(ruleInputsListFromMDMS, templateIds, inputFileTypes);
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
     * Return all properties that has isTrue flag as a true.
     * @param schemas schema object.
     * @param templateIds template ids list.
     * @param inputFileTypes list of file type.
     * @return
     */
    public static List<String> getIsTruePropertyFromSchema(List<Object> schemas, List<String> templateIds, List<String> inputFileTypes) {
        if (schemas == null) {
            return new ArrayList<>();
        }
        Set<String> finalData = new HashSet<>();
        for (Object item : schemas) {
            LinkedHashMap<?, ?> schemaEntity = (LinkedHashMap) item;
            if(!templateIds.contains(schemaEntity.get(MDMS_SCHEMA_SECTION)) || !inputFileTypes.contains(schemaEntity.get(MDMS_SCHEMA_TYPE))) continue;
            LinkedHashMap<String , LinkedHashMap> columns = (LinkedHashMap<String, LinkedHashMap>)((LinkedHashMap<String, LinkedHashMap>) schemaEntity.get(MDMS_SCHEMA_SCHEMA)).get(MDMS_SCHEMA_PROPERTIES);
            if(columns == null) return new ArrayList<>();
            for(Map.Entry<String, LinkedHashMap> column : columns.entrySet()){
                LinkedHashMap<String, Boolean> data = column.getValue();
                if(data.get(MDMS_SCHEMA_PROPERTIES_IS_REQUIRED)){
                    finalData.add(column.getKey());
                }
            }
        }
        return new ArrayList<>(finalData);
    }

    public void validateVehicleIdsFromAdditionalDetailsAgainstMDMS(PlanConfigurationRequest request, Object mdmsData)
    {
        List<String> vehicleIds = extractVehicleIdsFromAdditionalDetails(request.getPlanConfiguration().getAdditionalDetails());
    }

    public static List<String> extractVehicleIdsFromAdditionalDetails(Object additionalDetails) {
        try {
            String jsonString = objectMapper.writeValueAsString(additionalDetails);
            JsonNode rootNode = objectMapper.readTree(jsonString);

            List<String> vehicleIds = new ArrayList<>();
            JsonNode vehicleIdsNode = rootNode.get(VEHICLE_IDS);
            if (vehicleIdsNode != null && vehicleIdsNode.isArray()) {
                for (JsonNode idNode : vehicleIdsNode) {
                    vehicleIds.add(idNode.asText());
                }
            }

            return vehicleIds;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }
    }

    public void validateFilesActive(PlanConfiguration planConfigurationFromDB, PlanConfiguration planConfiguration)
    {
        // Create a map of files from planConfigurationFromDB using the file ID as the key
        Map<String, File> filesFromDBMap = planConfigurationFromDB.getFiles().stream()
                .collect(Collectors.toMap(File::getId, file -> file));

        // Iterate over the files in planConfiguration
        for (File file : planConfiguration.getFiles()) {
            File dbFile = filesFromDBMap.get(file.getId());
            // If the file exists in planConfigurationFromDB and has been made active after being inactive
            if (dbFile == null) {
                throw new CustomException("FILES_ACTIVE_STATUS_CHANGE_NOT_ALLOWED", "Files cannot be made active after being inactive, please upload new file");
            }
        }
    }
}
