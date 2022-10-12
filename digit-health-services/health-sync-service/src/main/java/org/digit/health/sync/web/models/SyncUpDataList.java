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

    public List<Map<Class<? extends SyncStep>, Object>> getStepToPayloadMapList() {
        Map<String, Map<Class<? extends SyncStep>, Object>> referenceIdToStepToPayloadMap =
                new HashMap<>();
        for (SyncUpData sData : syncUpData) {
            if (sData.getItems().get(0) instanceof HouseholdRegistration) {
                for (CampaignData cd : sData.getItems()) {
                    Map<Class<? extends SyncStep>, Object> stepToPayloadMap = new HashMap<>();
                    HouseholdRegistration hr = (HouseholdRegistration) cd;
                    stepToPayloadMap.put(RegistrationSyncStep.class,
                            HouseholdRegistrationMapper.INSTANCE.toRequest(hr));
                    referenceIdToStepToPayloadMap.put(hr.getClientReferenceId(), stepToPayloadMap);
                }
            }
            if (sData.getItems().get(0) instanceof Delivery) {
                for (CampaignData cd : sData.getItems()) {
                    Delivery delivery = (Delivery) cd;
                    Map<Class<? extends SyncStep>, Object> stepToPayloadMap = referenceIdToStepToPayloadMap
                            .get(delivery.getRegistrationClientReferenceId());
                    stepToPayloadMap.put(DeliverySyncStep.class,
                            DeliveryMapper.INSTANCE.toRequest(delivery));
                }
            }
        }
        return new ArrayList<>(referenceIdToStepToPayloadMap.values());
    }
}
