package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.web.models.CensusSearchCriteria;
import digit.web.models.CensusSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.workflow.BusinessService;
import org.egov.common.contract.workflow.State;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;

@Slf4j
@Component
public class CommonUtil {

    private ObjectMapper objectMapper;

    private BusinessServiceUtil businessServiceUtil;

    public CommonUtil(ObjectMapper objectMapper, BusinessServiceUtil businessServiceUtil) {
        this.objectMapper = objectMapper;
        this.businessServiceUtil = businessServiceUtil;
    }

    /**
     * Adds or updates the value of provided field in the additional details object.
     *
     * @param additionalDetails The current additional details object.
     * @param fieldToUpdate     The field that needs to be updated or added.
     * @param updatedValue      The new value to assign to the field.
     * @return A Map representing the updated additional details.
     */
    public Map<String, Object> updateFieldInAdditionalDetails(Object additionalDetails, String fieldToUpdate, String updatedValue) {
        try {

            // Get or create the additionalDetails as an ObjectNode
            ObjectNode objectNode = additionalDetails instanceof NullNode
                    ? objectMapper.createObjectNode()
                    : objectMapper.convertValue(additionalDetails, ObjectNode.class);

            // Update or Add the field in additional details object
            objectNode.put(fieldToUpdate, updatedValue);

            // Convert updated ObjectNode back to a Map and set it in 'additionalDetails'
            return objectMapper.convertValue(objectNode, Map.class);

        } catch (Exception e) {
            throw new CustomException(ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_CODE, ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_MESSAGE);
        }
    }

    public Map<String, Object> updateFieldInAdditionalDetails(Object additionalDetails, Map<String, Object> fieldsToBeUpdated) {
        try {

            // Get or create the additionalDetails as an ObjectNode
            ObjectNode objectNode = (additionalDetails == null || additionalDetails instanceof NullNode)
                    ? objectMapper.createObjectNode()
                    : objectMapper.convertValue(additionalDetails, ObjectNode.class);

            // Update or add the field in additional details object
            fieldsToBeUpdated.forEach((key, value) -> objectNode.set(key, objectMapper.valueToTree(value)));

            // Convert updated ObjectNode back to a Map
            return objectMapper.convertValue(objectNode, Map.class);

        } catch (Exception e) {
            throw new CustomException(ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_CODE, ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_MESSAGE + e);
        }
    }

    /**
     * Removes the field to be removed from the additional details object.
     *
     * @param additionalDetails The current additional details object.
     * @param fieldsToBeRemoved The list of field to be removed from additional details map
     * @return A Map representing the updated additional details after removal of the field.
     */
    public Map<String, Object> removeFieldFromAdditionalDetails(Object additionalDetails, String... fieldsToBeRemoved) {
        Map<String, Object> additionalDetailsMap = objectMapper.convertValue(additionalDetails, Map.class);
        for (String fieldToBeRemoved : fieldsToBeRemoved)
            additionalDetailsMap.remove(fieldToBeRemoved);

        return additionalDetailsMap;
    }

    /**
     * Creates the census search request for the provided details.
     * @param tenantId
     * @param planConfigId
     * @param boundariesToBeSearched
     * @param requestInfo
     * @return
     */
    public CensusSearchRequest getCensusSearchRequest(String tenantId, String planConfigId, Set<String> boundariesToBeSearched, RequestInfo requestInfo) {

        CensusSearchCriteria searchCriteria = CensusSearchCriteria.builder()
                .tenantId(tenantId)
                .source(planConfigId)
                .areaCodes(boundariesToBeSearched.stream().toList())
                .offset(0)
                .limit(boundariesToBeSearched.size())
                .build();

        return CensusSearchRequest.builder().requestInfo(requestInfo).censusSearchCriteria(searchCriteria).build();
    }

    /**
     * Creates a list of all the workflow states for the provided business service.
     *
     * @param requestInfo     The request information that is passed to fetch the business service.
     * @param businessService The business service identifier.
     * @param tenantId        The tenant ID for the business service.
     * @return A List of valid workflow states associated with the business service.
     */
    public List<String> getStatusFromBusinessService(RequestInfo requestInfo, String businessService, String tenantId) {
        BusinessService businessServices = businessServiceUtil.fetchBusinessService(requestInfo, businessService, tenantId);

        return businessServices.getStates().stream()
                .map(State::getState)
                .filter(state -> !ObjectUtils.isEmpty(state))
                .toList();
    }

    /**
     * Converts the provided additional details object to a PostgreSQL JSONB object.
     * Serializes the additional details into a JSON string and sets it as a PGobject of type "jsonb".
     *
     * @param additionalDetails The object containing additional details to be converted.
     * @return A PGobject containing the serialized JSON data for the additional details.
     */
    public PGobject convertToPgObject(Object additionalDetails) {
        PGobject pGobject = new PGobject();

        try {
            String json = objectMapper.writeValueAsString(additionalDetails);

            pGobject.setType(JSONB);
            pGobject.setValue(json);
        } catch (Exception e) {
            log.error(ERROR_WHILE_SETTING_JSONB_OBJECT, e);
        }

        return pGobject;
    }

    /**
     * Finds the unique elements in the primary list that are not present in the secondary list.
     * This can be used to determine newly added or missing elements between two lists.
     *
     * @param primaryList The main list containing elements to be checked.
     * @param secondaryList The reference list to compare against.
     * @return A set containing elements that are in primaryList but not in secondaryList.
     */
    public Set<String> getUniqueElements(List<String> primaryList, List<String> secondaryList) {
        Set<String> secondarySet = new HashSet<>(secondaryList);
        return primaryList.stream()
                .filter(element -> !secondarySet.contains(element))
                .collect(Collectors.toSet());
    }
}
