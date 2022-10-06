package org.digit.health.sync.context;

import org.digit.health.sync.context.enums.SyncErrorCode;
import org.digit.health.sync.context.metric.SyncMetric;
import org.digit.health.sync.context.step.SyncStep;
import org.egov.tracer.model.CustomException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observer;

public abstract class SyncContext implements Observer {
    protected SyncStep syncStep;

    private final Map<Class<? extends SyncStep>, Boolean> handlingStateMap;

    protected final List<SyncMetric> syncMetrics;

    protected SyncContext(SyncStep initialSyncStep) {
        this.syncStep = initialSyncStep;
        this.handlingStateMap = new HashMap<>();
        this.syncMetrics = new ArrayList<>();
    }

    public abstract void nextSyncStep();

    public abstract SyncStep getSyncStep();

    public abstract void setSyncStep(SyncStep syncStep);

    public abstract void handle(Object payload);

    protected void markHandled() {
        handlingStateMap.put(this.syncStep.getClass(),
                Boolean.TRUE);
    }

    protected void throwExceptionIfAlreadyHandled() {
        Class<? extends SyncStep> clazz = this.syncStep.getClass();
        if (Boolean.TRUE.equals(handlingStateMap.containsKey(clazz))) {
            throw new CustomException(SyncErrorCode.STEP_ALREADY_HANDLED.name(),
                    SyncErrorCode.STEP_ALREADY_HANDLED.message(clazz));
        }
    }

    public abstract boolean hasNext();

    public abstract List<SyncMetric> getSyncMetrics();
}
