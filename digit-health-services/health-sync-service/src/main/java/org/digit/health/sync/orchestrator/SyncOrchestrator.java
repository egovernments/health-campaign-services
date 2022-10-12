package org.digit.health.sync.orchestrator;

public interface SyncOrchestrator<I, O> {

    O orchestrate(I param);
}
