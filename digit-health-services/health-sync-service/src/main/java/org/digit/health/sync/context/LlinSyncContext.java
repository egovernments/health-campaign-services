package org.digit.health.sync.context;

import org.digit.health.sync.context.metric.SyncMetric;
import org.digit.health.sync.context.step.SyncStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Observable;

@Component
public class LlinSyncContext extends SyncContext {

    @Autowired
    public LlinSyncContext(@Qualifier("registrationSyncStep") SyncStep initialSyncStep) {
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
        this.syncStep = syncStep;
    }

    @Override
    public void handle(Object payload) {
        throwExceptionIfAlreadyHandled();
        this.syncStep.handle(payload);
        markHandled();
    }

    @Override
    public boolean hasNext() {
        return this.syncStep.hasNext();
    }

    @Override
    public List<SyncMetric> getSyncMetrics() {
        return this.syncMetrics;
    }

    @Override
    public void update(Observable o, Object syncMetric) {
        this.syncMetrics.add((SyncMetric) syncMetric);
    }
}
