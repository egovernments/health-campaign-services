package org.egov.transformer.service;

import org.egov.transformer.models.bill.*;
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

@Service
@Slf4j
public class BillService {

    private final ServiceRequestClient serviceRequestClient;
    private final TransformerProperties transformerProperties;

    public BillService(ServiceRequestClient serviceRequestClient, TransformerProperties transformerProperties) {
        this.serviceRequestClient = serviceRequestClient;
        this.transformerProperties = transformerProperties;
    }

    public String getBillNumber (String billId, String tenantId) {
        Bill bill = searchBill(billId, tenantId);
        if  (bill != null) {
            return bill.getBillNumber();
        }
        return null;
    }
    private Bill searchBill(String billId, String tenantId) {

        StringBuilder url = new StringBuilder().append(transformerProperties.getBillServiceHost());
        url.append(transformerProperties.getBillSearchUrl());
        RequestInfo requestInfo = RequestInfo.builder().userInfo(User.builder().uuid("transformer-uuid").build()).build();

        BillSearchRequest request = BillSearchRequest.builder()
                .requestInfo(requestInfo)
                .billCriteria(BillCriteria.builder()
                        .tenantId(tenantId)
                        .ids(Collections.singleton(billId)).build())
                .build();

        BillResponse billResponse;
        try {
            billResponse = serviceRequestClient.fetchResult(url, request, BillResponse.class);
            if (billResponse != null && billResponse.getBills() != null &&  !billResponse.getBills().isEmpty()) {
                return billResponse.getBills().get(0);
            }
        } catch (Exception e) {
            log.warn("Unable to fetch bill for billid={}, Exception: {}", billId, ExceptionUtils.getStackTrace(e));
            return null;
        }
        return null;
    }

    public ProcessInstance getLatestProcessInstance(String businessId, String tenantId) {
        ProcessInstanceResponse processInstanceResponse = searchProcessInstances(businessId, tenantId, false);
        if (processInstanceResponse != null && processInstanceResponse.getProcessInstances() != null && !processInstanceResponse.getProcessInstances().isEmpty()) {
            return processInstanceResponse.getProcessInstances().get(0);
        }
        return null;
    }

    private  ProcessInstanceResponse searchProcessInstances(String businessId, String tenantId, Boolean searchHistory) {

        StringBuilder url = new StringBuilder().append(transformerProperties.getWorkflowHost());
        RequestInfo requestInfo = RequestInfo.builder().userInfo(User.builder().uuid("transformer-uuid").build()).build();

        url.append(transformerProperties.getWorkflowProcessSearchUrl());
        url.append("?tenantId=");
        url.append(tenantId);
        url.append("&businessIds=");
        url.append(businessId);
        if (searchHistory) {
            url.append("&history=true");
        }

        RequestInfoWrapper infoWrapper = new RequestInfoWrapper();
        infoWrapper.setRequestInfo(requestInfo);
        ProcessInstanceResponse response;
        try {
            response = serviceRequestClient.fetchResult(url, infoWrapper, ProcessInstanceResponse.class);
        } catch (Exception e) {
            log.warn("Unable to fetch process instances for businessId={}, Exception: {}", businessId, ExceptionUtils.getStackTrace(e));
            return null;
        }
        return response;
    }

    public Map<String, Object> getWorkflowSummary(String businessId, String tenantId) {

        ProcessInstanceResponse response = searchProcessInstances(businessId, tenantId, true);

        if (response == null || response.getProcessInstances() == null || response.getProcessInstances().isEmpty()) {
            return Collections.emptyMap();
        }

        List<ProcessInstance> list = response.getProcessInstances();
        Map<String, Object> result = new HashMap<>();

        ProcessInstance latest = list.get(0);
        ProcessInstance oldest = list.get(list.size() - 1);

        if (latest.getState() != null) {
            result.put("currentStatus", latest.getState().getApplicationStatus());
        }

        long totalMinutes = (latest.getAuditDetails().getCreatedTime() - oldest.getAuditDetails().getCreatedTime()) / (1000 * 60);
        result.put("timeTakenFromInitialState", totalMinutes);

        Map<String, Long> transitionTimes = new LinkedHashMap<>();
        for (int i = 0; i < list.size() - 1; i++) {
            ProcessInstance current = list.get(i);
            ProcessInstance next = list.get(i + 1);

            if (current.getState() == null || next.getState() == null) {
                continue;
            }

            String key = next.getState().getApplicationStatus() + "_TO_" + current.getState().getApplicationStatus();
            long timeDiff = (current.getAuditDetails().getCreatedTime() - next.getAuditDetails().getCreatedTime()) / (1000 * 60);
            transitionTimes.put(key, timeDiff);
        }
        result.put("statusTransitionTimes", transitionTimes);

        return result;
    }
}
