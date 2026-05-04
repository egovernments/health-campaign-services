package org.egov.transformer.service;

import org.egov.transformer.models.bill.ProcessInstance;
import org.egov.transformer.models.bill.ProcessInstanceResponse;
import digit.models.coremodels.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.facility.*;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class BillService {

    private final ServiceRequestClient serviceRequestClient;
    private final TransformerProperties transformerProperties;

    public BillService(ServiceRequestClient serviceRequestClient, TransformerProperties transformerProperties) {
        this.serviceRequestClient = serviceRequestClient;
        this.transformerProperties = transformerProperties;
    }

    private ProcessInstanceResponse searchProcessInstances(String businessId, String tenantId) {

        StringBuilder url = new StringBuilder().append(transformerProperties.getWorkflowHost());
        RequestInfo requestInfo = RequestInfo.builder().userInfo(User.builder().uuid("transformer-uuid").build()).build();

        url.append(transformerProperties.getWorkflowProcessSearchUrl());
        url.append("?tenantId=");
        url.append(tenantId);
        url.append("&businessIds=");
        url.append(businessId);
        url.append("&history=true");

        RequestInfoWrapper infoWrapper = new RequestInfoWrapper();
        infoWrapper.setRequestInfo(requestInfo);
        ProcessInstanceResponse response = null;
        try {
            response = serviceRequestClient.fetchResult(url, infoWrapper, ProcessInstanceResponse.class);
        } catch (Exception e) {
            log.warn("Unable to fetch process instances for businessId={}, Exception: {}", businessId, ExceptionUtils.getStackTrace(e));
            return null;
        }
        return response;
    }

    public Map<String, Object> getWorkflowSummary(String businessId, String tenantId) {

        ProcessInstanceResponse response = searchProcessInstances(businessId, tenantId);

        if (response == null || response.getProcessInstances() == null || response.getProcessInstances().isEmpty()) {
            return Collections.emptyMap();
        }

        List<ProcessInstance> list = response.getProcessInstances();

        Map<String, Object> result = new HashMap<>();

        // 1. Current Status
        String currentStatus = list.get(0).getState().getApplicationStatus();
        result.put("currentStatus", currentStatus);

        // 2. Time from initial state
        long latestTime = list.get(0).getAuditDetails().getCreatedTime();
        long oldestTime = list.get(list.size() - 1).getAuditDetails().getCreatedTime();

        long totalMinutes = (latestTime - oldestTime) / (1000 * 60);
        result.put("timeTakenFromInitialState", totalMinutes);

        // 3. Time between consecutive states
        Map<String, Long> transitionTimes = new LinkedHashMap<>();

        for (int i = 0; i < list.size() - 1; i++) {

            ProcessInstance current = list.get(i);
            ProcessInstance next = list.get(i + 1);

            String fromStatus = next.getState().getApplicationStatus();
            String toStatus = current.getState().getApplicationStatus();

            long timeDiff = (current.getAuditDetails().getCreatedTime()
                    - next.getAuditDetails().getCreatedTime()) / (1000 * 60);

            String key = fromStatus + "_TO_" + toStatus;

            transitionTimes.put(key, timeDiff);
        }

        result.put("statusTransitionTimes", transitionTimes);

        return result;
    }
}
