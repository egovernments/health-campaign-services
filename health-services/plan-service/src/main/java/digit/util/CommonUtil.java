package digit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static digit.config.ServiceConstants.*;

@Component
@Slf4j
public class CommonUtil {

    private ObjectMapper objectMapper;

    public CommonUtil(ObjectMapper objectMapper)
    {
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
     * @return a field to extract from additional details
     */
    public <T> T extractFieldsFromJsonObject(Object additionalDetails, String fieldToExtract, Class<T> valueType) {
        try {
            String jsonString = objectMapper.writeValueAsString(additionalDetails);
            JsonNode rootNode = objectMapper.readTree(jsonString);

            JsonNode node = rootNode.get(fieldToExtract);
            if (node != null && node.isArray()) {
                return objectMapper.convertValue(node, objectMapper.getTypeFactory().constructCollectionType(List.class, valueType));
            } else if (node != null) {
                return objectMapper.convertValue(node, valueType);
            }
            // In case the node is of other type like object, handle accordingly
            return (T) node;
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
}
