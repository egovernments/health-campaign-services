package digit.util;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.sql.SQLException;
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
     * @param additionalDetails
     * @param fieldToUpdate
     * @param updatedValue
     * @return
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
            Map<String, Object> updatedDetails = objectMapper.convertValue(objectNode, Map.class);
            return updatedDetails;

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
     * @param additionalDetails
     * @param fieldToBeRemoved
     * @return
     */
    public Map<String, Object> removeFieldFromAdditionalDetails(Object additionalDetails, String fieldToBeRemoved) {
        Map<String, Object> additionalDetailsMap = objectMapper.convertValue(additionalDetails, Map.class);
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
     * @param requestInfo
     * @param businessService
     * @param tenantId
     * @return
     */
    public List<String> getStatusFromBusinessService(RequestInfo requestInfo, String businessService, String tenantId) {
        BusinessService businessServices = businessServiceUtil.fetchBusinessService(requestInfo, businessService, tenantId);

        return businessServices.getStates().stream()
                .map(State::getState)
                .filter(state -> !ObjectUtils.isEmpty(state))
                .toList();
    }

    public PGobject convertToPgObject(Object additionalDetails) {
        PGobject pGobject = new PGobject();

        try {
            String json = objectMapper.writeValueAsString(additionalDetails);

            pGobject.setType("jsonb");
            pGobject.setValue(json);
        } catch (JsonProcessingException e) {
            log.error("Error while processing JSON object to string", e);
        } catch (SQLException e) {
            log.error("Error while setting JSONB object", e);
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
        return primaryList.stream()
                .filter(element -> !secondaryList.contains(element))
                .collect(Collectors.toSet());
    }
}
