package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.web.models.CensusSearchCriteria;
import digit.web.models.CensusSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.workflow.BusinessService;
import org.egov.common.contract.workflow.State;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.*;

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
            ObjectNode objectNode = objectMapper.convertValue(additionalDetails, ObjectNode.class);

            // Update or Add the field in additional details object
            objectNode.put(fieldToUpdate, updatedValue);

            // Convert updated ObjectNode back to a Map and set it in 'additionalDetails'
            Map<String, Object> updatedDetails = objectMapper.convertValue(objectNode, Map.class);
            return updatedDetails;

        } catch (Exception e) {
            throw new CustomException(ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_CODE, ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_MESSAGE);
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
     * @param serviceBoundary
     * @return
     */
    public CensusSearchRequest getCensusSearchRequest(String tenantId, String planConfigId, String serviceBoundary, List<String> initiallySetServiceBoundaries, RequestInfo requestInfo) {
        Set<String> areaCodesForSearch = new HashSet<>();

        areaCodesForSearch.addAll(Arrays.asList(serviceBoundary.split(",")));
        areaCodesForSearch.addAll(initiallySetServiceBoundaries);

        CensusSearchCriteria searchCriteria = CensusSearchCriteria.builder()
                .tenantId(tenantId)
                .source(planConfigId)
                .areaCodes(areaCodesForSearch.stream().toList())
                .offset(0)
                .limit(areaCodesForSearch.size())
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
}
