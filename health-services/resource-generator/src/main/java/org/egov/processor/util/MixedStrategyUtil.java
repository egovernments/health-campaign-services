package org.egov.processor.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.processor.web.models.Operation;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.mdmsV2.Mdms;
import org.egov.processor.web.models.mdmsV2.MixedStrategyOperationLogic;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.processor.config.ServiceConstants.*;

@Component
public class MixedStrategyUtil {

    private MdmsV2Util mdmsV2Util;

    private ParsingUtil parsingUtil;

    private ObjectMapper mapper;

    public MixedStrategyUtil(MdmsV2Util mdmsV2Util, ParsingUtil parsingUtil, ObjectMapper mapper) {
        this.mdmsV2Util = mdmsV2Util;
        this.parsingUtil = parsingUtil;
        this.mapper = mapper;
    }

    /**
     * Fetches a list of MixedStrategyOperationLogic objects from MDMS based on the provided planConfigurationRequest.
     *
     * @param request The PlanConfigurationRequest containing the plan configuration and request info.
     * @return A list of MixedStrategyOperationLogic objects fetched from MDMS.
     */
    public List<MixedStrategyOperationLogic> fetchMixedStrategyOperationLogicFromMDMS(PlanConfigurationRequest request) {
        String rootTenantId = request.getPlanConfiguration().getTenantId().split("\\.")[0];
        List<Mdms> mdmsV2Data = mdmsV2Util.fetchMdmsV2Data(request.getRequestInfo(), rootTenantId, MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_MIXED_STRATEGY, null);

        return mdmsV2Data.stream()
                .map(mdms -> mapper.convertValue(mdms.getData(), MixedStrategyOperationLogic.class))
                .collect(Collectors.toList());

    }

    /**
     * Retrieves a list of categories that are restricted based on the provided details.
     * Returns an empty list if no match is found.
     *
     * @param isFixedPost       A boolean indicating whether the mapped facility is fixed post.
     * @param planConfiguration The plan configuration containing additional details.
     * @param logicList         A list of MixedStrategyOperationLogic objects to filter against.
     * @return A list of categories not allowed to have output value or an empty list if no matching logic is found.
     */
    public List<String> getCategoriesNotAllowed(boolean isFixedPost, PlanConfiguration planConfiguration, List<MixedStrategyOperationLogic> logicList) {

        //Extract fields from additional details
        String registrationProcess = (String) parsingUtil.extractFieldsFromJsonObject(planConfiguration.getAdditionalDetails(), REGISTRATION_PROCESS);
        String distributionProcess = (String) parsingUtil.extractFieldsFromJsonObject(planConfiguration.getAdditionalDetails(), DISTRIBUTION_PROCESS);

        // If any of the process detail value is null, return an empty list
        if (ObjectUtils.isEmpty(registrationProcess) || ObjectUtils.isEmpty(distributionProcess)) {
            return Collections.emptyList();
        }

        return logicList.stream()
                .filter(logic -> logic.isFixedPost() == isFixedPost)
                .filter(logic -> logic.getRegistrationProcess().equalsIgnoreCase(registrationProcess))
                .filter(logic -> logic.getDistributionProcess().equalsIgnoreCase(distributionProcess))
                .map(MixedStrategyOperationLogic::getCategoriesNotAllowed)
                .findAny()  // Returns any matching element since there is only one match
                .orElse(List.of());

    }

    /**
     * Nullifies result values in the map for outputs belonging to the categories not allowed.
     * Exits early if no restrictions are specified.
     *
     * @param resultMap            A map containing output keys and their corresponding result values.
     * @param operations           A list of operations.
     * @param categoriesNotAllowed A list of categories that are restricted and should not have associated outputs.
     */
    public void processResultMap(Map<String, BigDecimal> resultMap, List<Operation> operations, List<String> categoriesNotAllowed) {

        // If all te categories are allowed, don't process further.
        if(CollectionUtils.isEmpty(categoriesNotAllowed))
            return;

        // Map categories not allowed to its corresponding list of output keys
        Map<String, List<String>> categoryNotAllowedToOutputMap = operations.stream()
                .filter(op -> op.getActive() && categoriesNotAllowed.contains(op.getCategory()))
                .collect(Collectors.groupingBy(
                        Operation::getCategory,
                        Collectors.mapping(Operation::getOutput, Collectors.toList())));


        // Iterate through categories in the categoriesNotAllowed list and set their result values to null
        for (String category : categoriesNotAllowed) {
            List<String> outputKeys = categoryNotAllowedToOutputMap.getOrDefault(category, Collections.emptyList());
            for (String outputKey : outputKeys) {
                if (resultMap.containsKey(outputKey)) {
                    resultMap.put(outputKey, null);
                }
            }
        }
    }
}
