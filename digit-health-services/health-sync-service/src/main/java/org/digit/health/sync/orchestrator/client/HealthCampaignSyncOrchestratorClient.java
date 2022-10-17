package org.digit.health.sync.orchestrator.client;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.context.enums.StepSyncStatus;
import org.digit.health.sync.context.metric.SyncStepMetric;
import org.digit.health.sync.context.step.SyncStep;
import org.digit.health.sync.orchestrator.SyncOrchestrator;
import org.digit.health.sync.orchestrator.client.enums.SyncLogStatus;
import org.digit.health.sync.orchestrator.client.metric.SyncLogMetric;
import org.digit.health.sync.repository.SyncErrorDetailsLogRepository;
import org.digit.health.sync.web.models.AuditDetails;
import org.digit.health.sync.web.models.SyncUpDataList;
import org.digit.health.sync.web.models.dao.SyncErrorDetailsLogData;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HealthCampaignSyncOrchestratorClient
        implements SyncOrchestratorClient<Map<String, Object>, SyncLogMetric> {
    private final SyncOrchestrator<Map<Class<? extends SyncStep>, Object>,
            List<SyncStepMetric>> syncOrchestrator;
    private final SyncErrorDetailsLogRepository syncErrorDetailsLogRepository;

    @Autowired
    public HealthCampaignSyncOrchestratorClient(SyncOrchestrator<Map<Class<? extends SyncStep>, Object>,
            List<SyncStepMetric>> syncOrchestrator, @Qualifier("defaultSyncErrorDetailsLogRepository")
    SyncErrorDetailsLogRepository syncErrorDetailsLogRepository) {
        this.syncOrchestrator = syncOrchestrator;
        this.syncErrorDetailsLogRepository = syncErrorDetailsLogRepository;
    }


    @Override
    public SyncLogMetric orchestrate(Map<String, Object> payloadMap) {
        SyncUpDataList syncUpDataList = (SyncUpDataList) payloadMap.get("syncUpDataList");
        List<Map<Class<? extends SyncStep>, Object>> stepToPayloadMapList =
                syncUpDataList.getStepToPayloadMapList();
        List<SyncStepMetric> syncStepMetricList = new ArrayList<>();
        for (Map<Class<? extends SyncStep>, Object> stepToPayloadMap : stepToPayloadMapList) {
            List<SyncStepMetric> syncStepMetrics = syncOrchestrator.orchestrate(stepToPayloadMap);
            syncStepMetricList.addAll(syncStepMetrics);
        }
        SyncLogMetric syncLogMetric = getSyncLogMetric(syncStepMetricList);
        if (!syncLogMetric.getSyncLogStatus().equals(SyncLogStatus.COMPLETE)) {
            persistErrorDetails(payloadMap, syncStepMetricList);
        }
        return syncLogMetric;
    }

    private void persistErrorDetails(Map<String, Object> payloadMap, List<SyncStepMetric> syncStepMetricList) {
        syncStepMetricList.stream().filter(syncStepMetric ->
                syncStepMetric.getStatus().equals(StepSyncStatus.FAILED))
                .map(syncStepMetric -> SyncErrorDetailsLogData.builder()
                        .syncId((String) payloadMap.get("syncId"))
                        .recordIdType(syncStepMetric.getRecordIdType().name())
                        .recordId(syncStepMetric.getRecordId())
                        .syncErrorDetailsId(UUID.randomUUID().toString())
                        .tenantId((String) payloadMap.get("tenantId"))
                        .errorCodes(syncStepMetric.getErrorCode())
                        .errorMessages(syncStepMetric.getErrorMessage())
                        .auditDetails(getAuditDetails(payloadMap))
                        .build())
                .collect(Collectors.toList())
                .parallelStream()
                .forEach(syncErrorDetailsLogRepository::save);
    }

    private static AuditDetails getAuditDetails(Map<String, Object> payloadMap) {

         RequestInfo requestInfo = (RequestInfo) payloadMap.get("requestInfo");
         return AuditDetails.builder()
                 .createdBy(requestInfo.getUserInfo().getUuid())
                 .createdTime(System.currentTimeMillis())
                 .lastModifiedBy(requestInfo.getUserInfo().getUuid())
                 .createdTime(System.currentTimeMillis())
                 .build();
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
