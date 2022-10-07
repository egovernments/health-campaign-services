package org.digit.health.sync.context.step;

import org.digit.health.sync.context.SyncContext;
import org.digit.health.sync.context.enums.RecordIdType;
import org.digit.health.sync.context.enums.StepSyncStatus;
import org.digit.health.sync.context.enums.SyncErrorCode;
import org.digit.health.sync.context.metric.SyncMetric;

import java.util.Observable;

public abstract class SyncStep extends Observable {
    public abstract void nextSyncStep(SyncContext syncContext);

    public abstract void handle(Object payload);

    public abstract boolean hasNext();

    protected void publishFailureMetric(String recordId, RecordIdType recordIdType, String errorMessage) {
        this.setChanged();
        this.notifyObservers(SyncMetric.builder()
                .status(StepSyncStatus.FAILED)
                .recordId(recordId)
                .recordIdType(recordIdType)
                .errorCode(SyncErrorCode.ERROR_IN_REST_CALL.name())
                .errorMessage(SyncErrorCode.ERROR_IN_REST_CALL.message(errorMessage))
                .build());
    }

    protected void publishSuccessMetric(String recordId,
                                      RecordIdType recordIdType) {
        this.setChanged();
        this.notifyObservers(SyncMetric.builder()
                .status(StepSyncStatus.COMPLETED)
                .recordId(recordId)
                .recordIdType(recordIdType)
                .build());
    }
}
