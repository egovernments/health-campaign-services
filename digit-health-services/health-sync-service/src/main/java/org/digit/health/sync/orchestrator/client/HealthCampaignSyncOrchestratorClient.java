package org.digit.health.sync.orchestrator.client;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.context.enums.StepSyncStatus;
import org.digit.health.sync.context.metric.SyncStepMetric;
import org.digit.health.sync.context.step.DeliverySyncStep;
import org.digit.health.sync.context.step.RegistrationSyncStep;
import org.digit.health.sync.context.step.SyncStep;
import org.digit.health.sync.orchestrator.SyncOrchestrator;
import org.digit.health.sync.orchestrator.client.enums.SyncLogStatus;
import org.digit.health.sync.orchestrator.client.metric.SyncLogMetric;
import org.digit.health.sync.web.models.CampaignData;
import org.digit.health.sync.web.models.Delivery;
import org.digit.health.sync.web.models.HouseholdRegistration;
import org.digit.health.sync.web.models.SyncUpData;
import org.digit.health.sync.web.models.SyncUpDataList;
import org.digit.health.sync.web.models.request.DeliveryMapper;
import org.digit.health.sync.web.models.request.HouseholdRegistrationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class HealthCampaignSyncOrchestratorClient
        implements SyncOrchestratorClient<Map<String, Object>, SyncLogMetric> {
    private final SyncOrchestrator<Map<Class<? extends SyncStep>, Object>,
            List<SyncStepMetric>> syncOrchestrator;

    @Autowired
    public HealthCampaignSyncOrchestratorClient(SyncOrchestrator<Map<Class<? extends SyncStep>, Object>,
            List<SyncStepMetric>> syncOrchestrator) {
        this.syncOrchestrator = syncOrchestrator;
    }


    @Override
    public SyncLogMetric orchestrate(Map<String, Object> payloadMap) {
        SyncUpDataList syncUpDataList = (SyncUpDataList) payloadMap.get("syncUpDataList");
        List<SyncUpData> syncUpData = syncUpDataList.getSyncUpData();
        List<Map<Class<? extends SyncStep>, Object>> stepToPayloadMapList =
                getStepToPayloadMapList(syncUpData);
        List<SyncStepMetric> syncStepMetricList = orchestrate(stepToPayloadMapList);
        // TODO: Make entry in sync_error_details_log table in case of any errors
        return getSyncLogMetric(syncStepMetricList);
    }

    private List<Map<Class<? extends SyncStep>, Object>> getStepToPayloadMapList(List<SyncUpData> syncUpData) {
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

    private List<SyncStepMetric> orchestrate(List<Map<Class<? extends SyncStep>, Object>> stepToPayloadMapList) {
        List<SyncStepMetric> syncStepMetricList = new ArrayList<>();
        for (Map<Class<? extends SyncStep>, Object> stepToPayloadMap : stepToPayloadMapList) {
            List<SyncStepMetric> syncStepMetrics = syncOrchestrator.orchestrate(stepToPayloadMap);
            syncStepMetricList.addAll(syncStepMetrics);
        }
        return syncStepMetricList;
    }

    private SyncLogMetric getSyncLogMetric(List<SyncStepMetric> syncStepMetrics) {
        long successCount = syncStepMetrics.stream().filter(syncStepMetric -> syncStepMetric
                .getStatus().equals(StepSyncStatus.COMPLETED)).count();
        long errorCount = syncStepMetrics.stream().filter(syncStepMetric -> syncStepMetric
                .getStatus().equals(StepSyncStatus.FAILED)).count();
        return SyncLogMetric.builder()
                .totalCount(syncStepMetrics.size())
                .successCount(successCount)
                .errorCount(errorCount)
                .syncLogStatus(getSyncLogStatus(successCount, errorCount, syncStepMetrics.size()))
                .build();
    }

    private SyncLogStatus getSyncLogStatus(long successCount, long errorCount, int totalCount) {
        if ((totalCount > (successCount + errorCount))
                || (errorCount > 0 && successCount > 0)) {
            return SyncLogStatus.PARTIALLY_COMPLETE;
        } else if (totalCount == successCount) {
            return SyncLogStatus.COMPLETE;
        } else {
            return SyncLogStatus.FAILED;
        }
    }
}
