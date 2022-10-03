package org.digit.health.sync.context;

import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class LlinSyncContext implements SyncContext {
    private SyncStep syncStep;

    private final Map<Class<? extends SyncStep>, Boolean> handlingStateMap;

    @Autowired
    public LlinSyncContext(@Qualifier("registrationSyncStep") SyncStep initialSyncStep) {
        this.syncStep = initialSyncStep;
        handlingStateMap = new HashMap<>();
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

    private void markHandled() {
        handlingStateMap.put(this.syncStep.getClass(),
                Boolean.TRUE);
    }

    private void throwExceptionIfAlreadyHandled() {
        Class<? extends SyncStep> clazz = this.syncStep.getClass();
        if (Boolean.TRUE.equals(handlingStateMap.containsKey(clazz))) {
            throw new CustomException(SyncErrorCode.STEP_ALREADY_HANDLED.name(),
                    SyncErrorCode.STEP_ALREADY_HANDLED.message(clazz));
        }
    }
}
