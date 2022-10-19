package org.digit.health.sync.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.digit.health.sync.context.step.DeliverySyncStep;
import org.digit.health.sync.context.step.RegistrationSyncStep;
import org.digit.health.sync.context.step.SyncStep;
import org.digit.health.sync.web.models.request.DeliveryMapper;
import org.digit.health.sync.web.models.request.HouseholdRegistrationMapper;
import org.digit.health.sync.web.models.request.HouseholdRegistrationRequest;
import org.digit.health.sync.web.models.request.ResourceDeliveryRequest;
import org.egov.common.contract.request.RequestInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SyncUpDataList {

    @JsonProperty("syncUpData")
    private List<SyncUpData> syncUpData;

    public List<Map<Class<? extends SyncStep>, Object>> getStepToPayloadMapList(RequestInfo requestInfo) {
        Map<String, Map<Class<? extends SyncStep>, Object>> referenceIdToStepToPayloadMap =
                new HashMap<>();
        for (SyncUpData sData : syncUpData) {
            if (sData.getItems().get(0) instanceof HouseholdRegistration) {
                for (CampaignData cd : sData.getItems()) {
                    Map<Class<? extends SyncStep>, Object> stepToPayloadMap = new HashMap<>();
                    HouseholdRegistration hr = (HouseholdRegistration) cd;
                    HouseholdRegistrationRequest request = HouseholdRegistrationMapper.INSTANCE.toRequest(hr);
                    request.setRequestInfo(requestInfo);
                    stepToPayloadMap.put(RegistrationSyncStep.class, request);
                    referenceIdToStepToPayloadMap.put(hr.getHousehold().getClientReferenceId(),
                            stepToPayloadMap);
                }
            }
            if (sData.getItems().get(0) instanceof ResourceDelivery) {
                for (CampaignData cd : sData.getItems()) {
                    ResourceDelivery resourceDelivery = (ResourceDelivery) cd;
                    ResourceDeliveryRequest request = DeliveryMapper.INSTANCE.toRequest(resourceDelivery);
                    request.setRequestInfo(requestInfo);
                    Map<Class<? extends SyncStep>, Object> stepToPayloadMap = referenceIdToStepToPayloadMap
                            .get(resourceDelivery.getDelivery().getRegistrationClientReferenceId());
                    if (stepToPayloadMap == null) {
                        Map<Class<? extends SyncStep>, Object> newStepToPayloadMap = new HashMap<>();
                        newStepToPayloadMap.put(DeliverySyncStep.class, request);
                        referenceIdToStepToPayloadMap.put(resourceDelivery
                                .getDelivery().getClientReferenceId(), newStepToPayloadMap);
                    } else {
                        stepToPayloadMap.put(DeliverySyncStep.class, request);
                    }
                }
            }
        }
        return new ArrayList<>(referenceIdToStepToPayloadMap.values());
    }

    public long getTotalCount() {
         return syncUpData.stream().mapToLong(item -> item.getItems().size()).sum();
    }
}
