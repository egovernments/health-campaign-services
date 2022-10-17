package org.digit.health.sync.context;

import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.context.enums.SyncErrorCode;
import org.digit.health.sync.context.metric.SyncStepMetric;
import org.digit.health.sync.context.step.SyncStep;
import org.egov.tracer.model.CustomException;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observer;

@Component
@Scope("prototype")
@Slf4j
public abstract class SyncContext implements Observer {
    protected SyncStep syncStep;

    private final Map<Class<? extends SyncStep>, Boolean> handlingStateMap;

    protected final List<SyncStepMetric> syncStepMetrics;

    protected SyncContext(SyncStep initialSyncStep) {
        this.syncStep = initialSyncStep;
        this.handlingStateMap = new HashMap<>();
        this.syncStepMetrics = new ArrayList<>();
        this.syncStep.addObserver(this);
    }

    public abstract void nextSyncStep();

    public abstract SyncStep getSyncStep();

    public void setSyncStep(SyncStep syncStep) {
        syncStep.addObserver(this);
    }

    public abstract void handle(Object payload);

    protected void markHandled() {
        handlingStateMap.put(this.syncStep.getClass(),
                Boolean.TRUE);
    }

    protected void throwExceptionIfAlreadyHandled() {
        Class<? extends SyncStep> clazz = this.syncStep.getClass();
        if (Boolean.TRUE.equals(handlingStateMap.containsKey(clazz))) {
            log.error("Sync step {} already handled", clazz.getName());
            throw new CustomException(SyncErrorCode.STEP_ALREADY_HANDLED.name(),
                    SyncErrorCode.STEP_ALREADY_HANDLED.message(clazz));
        }
    }

    public abstract boolean hasNext();

    public abstract List<SyncStepMetric> getSyncMetrics();
}
