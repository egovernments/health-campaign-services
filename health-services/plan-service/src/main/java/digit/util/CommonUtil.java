package digit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
     * Extracts the list of vehicle Ids provided in additional details object
     *
     * @param additionalDetails the additionalDetails object from PlanConfigurationRequest
     * @return a list of vehicle Ids from additional details
     */
    public Object extractFieldsFromAdditionalDetails(Object additionalDetails, String fieldToExtract) {
        try {
            String jsonString = objectMapper.writeValueAsString(additionalDetails);
            JsonNode rootNode = objectMapper.readTree(jsonString);

            List<String> listFromAdditionalDetails = new ArrayList<>();
            JsonNode node = rootNode.get(fieldToExtract);
            if (node != null && node.isArray()) {
                for (JsonNode idNode : node) {
                    listFromAdditionalDetails.add(idNode.asText());
                }
                return listFromAdditionalDetails;
            } else if (node != null) {
                // Return the value in its original type based on its type
                if (node.isInt()) {
                    return node.asInt();
                } else if (node.isBoolean()) {
                    return node.asBoolean();
                } else if (node.isTextual()) {
                    return node.asText();
                }
            }
            // In case the node is of some other type (like object or binary), handle accordingly
            return node;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
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

        StringBuilder jsonPathFilters = new StringBuilder("[?(");
        jsonPathFilters.append(JSON_FIELD_CAMPAIGN_TYPE).append(EQUALS).append(SINGLE_QUOTE).append(campaignType).append(SINGLE_QUOTE)
                .append(AND).append(JSON_FIELD_DISTRIBUTION_PROCESS).append(EQUALS).append(SINGLE_QUOTE).append(distributionProcess).append(SINGLE_QUOTE)
                .append(AND).append(JSON_FIELD_REGISTRATION_PROCESS).append(EQUALS).append(SINGLE_QUOTE).append(registrationProcess).append(SINGLE_QUOTE)
                .append(AND).append(JSON_FIELD_RESOURCE_DISTRIBUTION_STRATEGY_CODE).append(EQUALS).append(SINGLE_QUOTE).append(resourceDistributionStrategyCode).append(SINGLE_QUOTE)
                .append(AND).append(JSON_FIELD_IS_REGISTRATION_AND_DISTRIBUTION_TOGETHER).append(EQUALS).append(SINGLE_QUOTE).append(isRegistrationAndDistributionTogether).append(SINGLE_QUOTE)
                .append(")]");

        return JSON_ROOT_PATH + MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_ASSUMPTION + jsonPathFilters + FILTER_ALL_ASSUMPTIONS;
    }
}
