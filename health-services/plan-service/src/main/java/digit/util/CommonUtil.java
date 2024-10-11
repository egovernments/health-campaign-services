package digit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import digit.repository.PlanConfigurationRepository;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static digit.config.ServiceConstants.*;

@Component
@Slf4j
public class CommonUtil {

    private PlanConfigurationRepository planConfigurationRepository;

    private ObjectMapper objectMapper;

    public CommonUtil(PlanConfigurationRepository planConfigurationRepository, ObjectMapper objectMapper) {
        this.planConfigurationRepository = planConfigurationRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates the given input string against the provided regex pattern.
     *
     * @param patternString the regex pattern to validate against
     * @param inputString   the input string to be validated
     * @return true if the input string matches the regex pattern, false otherwise
     */
    public Boolean validateStringAgainstRegex(String patternString, String inputString) {
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(inputString);
        return matcher.matches();
    }


    /**
     * Extracts provided field from the additional details object
     *
     * @param additionalDetails the additionalDetails object from PlanConfigurationRequest
     * @param fieldToExtract the name of the field to be extracted from the additional details
     * @return the value of the specified field as a string
     * @throws CustomException if the field does not exist
     */
    public String extractFieldsFromJsonObject(Object additionalDetails, String fieldToExtract) {
        try {
            String jsonString = objectMapper.writeValueAsString(additionalDetails);
            JsonNode rootNode = objectMapper.readTree(jsonString);

            JsonNode node = rootNode.get(fieldToExtract);
            if (node != null) {
                // Convert the node to a String
                return objectMapper.convertValue(node, String.class);
            }
            // Return null if the node is empty
            return null;
        } catch (Exception e) {
            log.error(e.getMessage() + fieldToExtract);
            throw new CustomException(PROVIDED_KEY_IS_NOT_PRESENT_IN_JSON_OBJECT_CODE, PROVIDED_KEY_IS_NOT_PRESENT_IN_JSON_OBJECT_MESSAGE + fieldToExtract);
        }
    }

    /**
     * Extracts provided field from the additional details object
     *
     * @param additionalDetails the additionalDetails object from PlanConfigurationRequest
     * @param fieldToExtract the name of the field to be extracted from the additional details
     * @return the value of the specified field as a list of string
     * @throws CustomException if the field does not exist
     */
    public List<String> extractFieldsFromJsonObject(Object additionalDetails, String fieldToExtract, Class<List> valueType) {
        try {
            String jsonString = objectMapper.writeValueAsString(additionalDetails);
            JsonNode rootNode = objectMapper.readTree(jsonString);

            JsonNode node = rootNode.get(fieldToExtract);
            List<String> list = new ArrayList<>();
            if (node != null && node.isArray()) {
                for (JsonNode idNode : node) {
                    list.add(idNode.asText());
                }
            }
            return list;
        } catch (Exception e) {
            log.error(e.getMessage() + fieldToExtract);
            throw new CustomException(PROVIDED_KEY_IS_NOT_PRESENT_IN_JSON_OBJECT_CODE, PROVIDED_KEY_IS_NOT_PRESENT_IN_JSON_OBJECT_MESSAGE + fieldToExtract);
        }
    }

    /**
     * Constructs a JSONPath expression used to filter assumptions based on the given parameters -
     * campaign type, distribution process, registration process, resource distribution strategy,
     * and whether registration and distribution are together match the provided values.
     *
     * @param campaignType                          The type of campaign to filter by (e.g., "Health", "Education").
     * @param distributionProcess                   The process of distribution to filter by (e.g., "Central", "Decentralized").
     * @param registrationProcess                   The registration process to filter by (e.g., "Online", "In-Person").
     * @param resourceDistributionStrategyCode      The strategy code for resource distribution to filter by (e.g., "Strategy1").
     * @param isRegistrationAndDistributionTogether Whether registration and distribution are combined, to filter by ("true"/"false").
     * @return A JSONPath expression string that filters assumptions based on the given criteria.
     */
    public String createJsonPathForAssumption(
            String campaignType,
            String distributionProcess,
            String registrationProcess,
            String resourceDistributionStrategyCode,
            String isRegistrationAndDistributionTogether
    ) {

        StringBuilder jsonPathFilters = new StringBuilder(JSONPATH_FILTER_PREFIX);
        jsonPathFilters.append(JSON_PATH_FILTER_CAMPAIGN_TYPE).append(EQUALS).append(SINGLE_QUOTE).append(StringEscapeUtils.escapeJson(campaignType)).append(SINGLE_QUOTE)
                .append(AND).append(JSON_PATH_FILTER_DISTRIBUTION_PROCESS).append(EQUALS).append(SINGLE_QUOTE).append(StringEscapeUtils.escapeJson(distributionProcess)).append(SINGLE_QUOTE)
                .append(AND).append(JSON_PATH_FILTER_REGISTRATION_PROCESS).append(EQUALS).append(SINGLE_QUOTE).append(StringEscapeUtils.escapeJson(registrationProcess)).append(SINGLE_QUOTE)
                .append(AND).append(JSON_PATH_FILTER_RESOURCE_DISTRIBUTION_STRATEGY_CODE).append(EQUALS).append(SINGLE_QUOTE).append(StringEscapeUtils.escapeJson(resourceDistributionStrategyCode)).append(SINGLE_QUOTE)
                .append(AND).append(JSON_PATH_FILTER_IS_REGISTRATION_AND_DISTRIBUTION_TOGETHER).append(EQUALS).append(SINGLE_QUOTE).append(StringEscapeUtils.escapeJson(isRegistrationAndDistributionTogether)).append(SINGLE_QUOTE)
                .append(JSONPATH_FILTER_SUFFIX);

        return JSON_ROOT_PATH + MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_ASSUMPTION + jsonPathFilters + FILTER_ALL_ASSUMPTIONS;
    }


    /**
     * Searches the plan config based on the plan config id provided
     *
     * @param planConfigId the plan config id to validate
     * @param tenantId     the tenant id of the plan config
     * @return list of planConfiguration for the provided plan config id
     */
    public List<PlanConfiguration> searchPlanConfigId(String planConfigId, String tenantId) {
        List<PlanConfiguration> planConfigurations = planConfigurationRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(planConfigId)
                .tenantId(tenantId)
                .build());

        return planConfigurations;
    }

    /**
     * Validates the user information within the provided PlanConfigurationRequest.
     *
     * @param requestInfo the request info containing the user information to be validated
     * @throws CustomException if the user information is missing in the request
     */
    public void validateUserInfo(RequestInfo requestInfo)
    {
        if (ObjectUtils.isEmpty(requestInfo.getUserInfo())) {
            log.error(USERINFO_MISSING_MESSAGE);
            throw new CustomException(USERINFO_MISSING_CODE, USERINFO_MISSING_MESSAGE);
        }
    }

    /**
     * This is a helper method to get the lowest and highest hierarchy for microplan from MDMS
     *
     * @param mdmsData the mdms data
     * @return returns the lowest and highest hierarchy for microplan
     */
    public Map<String, String> getMicroplanHierarchy(Object mdmsData) {

        String jsonPathForMicroplanHierarchy = JSON_ROOT_PATH + MDMS_ADMIN_CONSOLE_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_HIERARCHY_CONFIG + HIERARCHY_CONFIG_FOR_MICROPLAN;

        List<Map<String, String>> hierarchyForMicroplan;

        try {
            log.info(jsonPathForMicroplanHierarchy);
            hierarchyForMicroplan = JsonPath.read(mdmsData, jsonPathForMicroplanHierarchy);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
        }

        Map<String, String> hierarchyMap = new HashMap<>();
        hierarchyMap.put(LOWEST_HIERARCHY_FIELD_FOR_MICROPLAN, hierarchyForMicroplan.get(0).get(LOWEST_HIERARCHY_FIELD_FOR_MICROPLAN));
        hierarchyMap.put(HIGHEST_HIERARCHY_FIELD_FOR_MICROPLAN, hierarchyForMicroplan.get(0).get(HIGHEST_HIERARCHY_FIELD_FOR_MICROPLAN));

        return hierarchyMap;
    }
}
