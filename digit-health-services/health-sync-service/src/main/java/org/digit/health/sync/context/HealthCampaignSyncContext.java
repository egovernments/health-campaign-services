package org.digit.health.sync.context;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.context.metric.SyncStepMetric;
import org.digit.health.sync.context.step.SyncStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Observable;

@Component
@Slf4j
@Scope("prototype")
public class HealthCampaignSyncContext extends SyncContext {

    @Autowired
    public HealthCampaignSyncContext(@Qualifier("registrationSyncStep") SyncStep initialSyncStep) {
        super(initialSyncStep);
    }

    @Override
    public void nextSyncStep() {
        syncStep.nextSyncStep(this);
    }

    @Override
    public SyncStep getSyncStep() {
        return this.syncStep;
    }

    @Override
    public void setSyncStep(SyncStep syncStep) {
        super.setSyncStep(syncStep);
        this.syncStep = syncStep;
    }

    @Override
    public void handle(Object payload) {
        throwExceptionIfAlreadyHandled();
        log.info("Handling {}", this.syncStep.getClass().getName());
        this.syncStep.handle(payload);
        markHandled();
    }

    @Override
    public boolean hasNext() {
        return this.syncStep.hasNext();
    }

    @Override
    public List<SyncStepMetric> getSyncMetrics() {
        return this.syncStepMetrics;
    }

    @Override
    public void update(Observable o, Object syncMetric) {
        this.syncStepMetrics.add((SyncStepMetric) syncMetric);
    }
}
