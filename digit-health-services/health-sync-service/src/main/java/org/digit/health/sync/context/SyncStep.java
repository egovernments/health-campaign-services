package org.digit.health.sync.context;

public interface SyncStep {
    void nextSyncStep(SyncContext syncContext);

    void handle(Object payload);

    boolean hasNext();
}
