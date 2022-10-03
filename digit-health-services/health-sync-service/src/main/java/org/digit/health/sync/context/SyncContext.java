package org.digit.health.sync.context;

public interface SyncContext {

    void nextSyncStep();

    SyncStep getSyncStep();

    void setSyncStep(SyncStep syncStep);

    void handle(Object payload);

    boolean hasNext();
}
