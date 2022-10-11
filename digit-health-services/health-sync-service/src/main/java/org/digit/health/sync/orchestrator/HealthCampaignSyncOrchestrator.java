package org.digit.health.sync.orchestrator;

import org.digit.health.sync.context.HealthCampaignSyncContext;
import org.digit.health.sync.context.SyncContext;
import org.digit.health.sync.context.step.SyncStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HealthCampaignSyncOrchestrator implements SyncOrchestrator {
    private final ApplicationContext applicationContext;

    @Autowired
    public HealthCampaignSyncOrchestrator(ApplicationContext applicationContext) {

        this.applicationContext = applicationContext;
    }

    @Override
    public Object orchestrate(Object param) {
        Map<Class<? extends SyncStep>, Object> stepToPayloadMap =
                (Map<Class<? extends SyncStep>, Object>) param;
        SyncContext syncContext = applicationContext.getBean(HealthCampaignSyncContext.class);
        // TODO: Handle error scenario
        Class<? extends SyncStep> clazz = syncContext.getSyncStep().getClass();
        syncContext.handle(stepToPayloadMap.get(clazz));
        while (syncContext.hasNext()) {
            syncContext.nextSyncStep();
            clazz = syncContext.getSyncStep().getClass();
            syncContext.handle(stepToPayloadMap.get(clazz));
        }
        return syncContext.getSyncMetrics();
    }
}
