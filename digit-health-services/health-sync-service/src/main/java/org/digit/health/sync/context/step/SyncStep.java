package org.digit.health.sync.context.step;

import org.digit.health.sync.context.SyncContext;

import java.util.Observable;

public abstract class SyncStep extends Observable {
    public abstract void nextSyncStep(SyncContext syncContext);

    public abstract void handle(Object payload);

    public abstract boolean hasNext();
}
