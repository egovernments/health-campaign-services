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
    public List<String> extractVehicleIdsFromAdditionalDetails(Object additionalDetails) {
        try {
            String jsonString = objectMapper.writeValueAsString(additionalDetails);
            JsonNode rootNode = objectMapper.readTree(jsonString);

            List<String> vehicleIds = new ArrayList<>();
            JsonNode vehicleIdsNode = rootNode.get(VEHICLE_ID_FIELD);
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
}
