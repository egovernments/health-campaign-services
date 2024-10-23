package digit.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.web.models.CensusSearchCriteria;
import digit.web.models.CensusSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static digit.config.ServiceConstants.ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_MESSAGE;
import static digit.config.ServiceConstants.ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_CODE;

@Component
public class CommonUtil {

    private ObjectMapper objectMapper;

    public CommonUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> updateFieldInAdditionalDetails(Object additionalDetails, String fieldToUpdate, String updatedValue) {
        try {

            String jsonString = objectMapper.writeValueAsString(additionalDetails);
            JsonNode rootNode = objectMapper.readTree(jsonString);

            // Cast rootNode to ObjectNode to allow modifications
            ObjectNode objectNode = (ObjectNode) rootNode;

            // Update or Add facilityId in additional details object
            objectNode.put(fieldToUpdate, updatedValue);

            // Convert updated ObjectNode back to a Map and set it in 'additionalDetails'
            Map<String, Object> updatedDetails = objectMapper.convertValue(objectNode, Map.class);
            return updatedDetails;

        } catch (JsonProcessingException e) {
            throw new CustomException(ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_CODE, ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_MESSAGE);
        }
    }

    public CensusSearchRequest getCensusSearchRequest(String tenantId, String planConfigId, String serviceBoundary) {
        CensusSearchCriteria searchCriteria = CensusSearchCriteria.builder()
                .tenantId(tenantId)
                .source(planConfigId)
                .areaCodes(List.of(serviceBoundary.split(", ")))
                .effectiveTo(0L)
                .build();

        return CensusSearchRequest.builder().censusSearchCriteria(searchCriteria).build();
    }
}
