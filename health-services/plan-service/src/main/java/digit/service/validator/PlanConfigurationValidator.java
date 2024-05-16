package digit.service.validator;

import com.jayway.jsonpath.JsonPath;

import digit.config.ServiceConstants;
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
        validateMappedToForLocality(planConfiguration);
        
        validateResourceMappingAgainstMDMS(request, mdmsData);
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
     * Validates that the 'mappedTo' field in the list of ResourceMappings contains the value "Locality".
     *
     * @param planConfiguration The plan configuration object to validate
     */
    public void validateMappedToForLocality(PlanConfiguration planConfiguration) {
        boolean hasLocality = planConfiguration.getResourceMapping().stream()
                .anyMatch(mapping -> LOCALITY_CODE.equalsIgnoreCase(mapping.getMappedTo()));
        if (!hasLocality) {
            throw new CustomException(LOCALITY_NOT_PRESENT_IN_MAPPED_TO_CODE, LOCALITY_NOT_PRESENT_IN_MAPPED_TO_MESSAGE);
        }
    }



    /**
     * Validates that all mappings in the list have the expected 'mappedTo' value.
     * If none of the mappings match the expected value, a CustomException is thrown.
     *
     * @param mappings        The list of ResourceMappings to validate.
     * @param expectedMappedTo The expected value for the 'mappedTo' field.
     */
    private void validateMappedTo(List<ResourceMapping> mappings, String expectedMappedTo) {
        boolean foundMatch = false;
        for (ResourceMapping mapping : mappings) {
            if (mapping.getMappedTo().equalsIgnoreCase(expectedMappedTo)) {
                foundMatch = true;
                break;
            }
        }
        if (!foundMatch) {
            throw new CustomException(MAPPED_TO_VALIDATION_ERROR_CODE,
                    "Atleast one resource's 'mappedTo' must be '" + expectedMappedTo + "'");
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

        validateAssumptionKeyAgainstMDMS(request, mdmsData);
        validateAssumptionValue(planConfiguration);
        validateFilestoreId(planConfiguration);
        validateTemplateIdentifierAgainstMDMS(request, mdmsData);
        validateOperationsInputAgainstMDMS(request, mdmsData);
        validateMappedToForLocality(planConfiguration);
        
        validateResourceMappingAgainstMDMS(request, mdmsData);

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
    
    /**
     * Validates the operations input against the Master Data Management System (MDMS) data.
     *
     * @param request  The PlanConfigurationRequest containing the plan configuration and other details.
     * @param mdmsData The MDMS data containing the master rule configure inputs.
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
        List<String> allowedColumns = getIsTrueColoumnFromSchema(ruleInputsListFromMDMS, templateIds, inputFileTypes);
        List<ResourceMapping> resourceMapping1 =  planConfiguration.getResourceMapping();  
        if(allowedColumns.contains(ServiceConstants.BOUNDARY_CODE)) {
        	Stream<ResourceMapping> d = resourceMapping1.stream().filter(f-> f.getMappedTo().equals(ServiceConstants.BOUNDARY_CODE));
        if(!(d.count()>0)) {        	
            throw new CustomException(REQUIRED_MAPPING_NOT_FOUND_CODE, REQUIRED_MAPPING_NOT_FOUND_MESSAGE);
        }
        }
    }
    
    public static List<String> getIsTrueColoumnFromSchema(List<Object> schemas, List<String> templateIds, List<String> inputFileTypes) {
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
}
