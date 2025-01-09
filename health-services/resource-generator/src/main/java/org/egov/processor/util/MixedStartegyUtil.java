package org.egov.processor.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.processor.web.models.Operation;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.mdmsV2.Mdms;
import org.egov.processor.web.models.mdmsV2.MixedStrategyOperationLogic;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.processor.config.ServiceConstants.*;
import static org.egov.processor.config.ServiceConstants.MDMS_SCHEMA_ADMIN_SCHEMA;

@Component
public class MixedStartegyUtil {

    private MdmsV2Util mdmsV2Util;

    private ParsingUtil parsingUtil;

    private ObjectMapper mapper;

    public MixedStartegyUtil(MdmsV2Util mdmsV2Util, ParsingUtil parsingUtil, ObjectMapper mapper) {
        this.mdmsV2Util = mdmsV2Util;
        this.parsingUtil = parsingUtil;
        this.mapper = mapper;
    }

    public List<MixedStrategyOperationLogic> fetchMixedStrategyOperationLogicFromMDMS(PlanConfigurationRequest request) {
        String rootTenantId = request.getPlanConfiguration().getTenantId().split("\\.")[0];
        List<Mdms> mdmsV2Data = mdmsV2Util.fetchMdmsV2Data(request.getRequestInfo(), rootTenantId, MDMS_PLAN_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_MIXED_STRATEGY, null);

        return mdmsV2Data.stream()
                .map(mdms -> mapper.convertValue(mdms.getData(), MixedStrategyOperationLogic.class))
                .collect(Collectors.toList());

    }

    public List<String> getCategoriesNotAllowed(boolean isFixedPost,
                                                PlanConfiguration planConfiguration, List<MixedStrategyOperationLogic> logicList) {

        return logicList.stream()
                .filter(logic -> logic.isFixedPost() == isFixedPost &&
                        logic.getRegistrationProcess().equalsIgnoreCase((String) parsingUtil.extractFieldsFromJsonObject(planConfiguration.getAdditionalDetails(), REGISTRATION_PROCESS)) &&
                        logic.getDistributionProcess().equalsIgnoreCase((String) parsingUtil.extractFieldsFromJsonObject(planConfiguration.getAdditionalDetails(), DISTRIBUTION_PROCESS)))
                .map(MixedStrategyOperationLogic::getCategoriesNotAllowed)
                .findAny()  // Returns any matching element since there is only one match
                .orElse(List.of());

    }

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
