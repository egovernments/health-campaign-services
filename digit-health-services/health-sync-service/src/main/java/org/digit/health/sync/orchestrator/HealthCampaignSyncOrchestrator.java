package org.digit.health.sync.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.context.HealthCampaignSyncContext;
import org.digit.health.sync.context.SyncContext;
import org.digit.health.sync.context.metric.SyncStepMetric;
import org.digit.health.sync.context.step.SyncStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class HealthCampaignSyncOrchestrator
        implements SyncOrchestrator<Map<Class<? extends SyncStep>, Object>, List<SyncStepMetric>> {
    private final ApplicationContext applicationContext;

    @Autowired
    public HealthCampaignSyncOrchestrator(ApplicationContext applicationContext) {

        this.applicationContext = applicationContext;
    }

    @Override
    public List<SyncStepMetric> orchestrate(Map<Class<? extends SyncStep>, Object> stepToPayloadMap) {
        SyncContext syncContext = applicationContext.getBean(HealthCampaignSyncContext.class);
        try {
            Class<? extends SyncStep> clazz = syncContext.getSyncStep().getClass();
            syncContext.handle(stepToPayloadMap.get(clazz));
            while (syncContext.hasNext()) {
                syncContext.nextSyncStep();
                clazz = syncContext.getSyncStep().getClass();
                syncContext.handle(stepToPayloadMap.get(clazz));
            }
        } catch (Exception exception) {
            log.error("Exception occurred during orchestration", exception);
        }
        return syncContext.getSyncMetrics();
    }
}
