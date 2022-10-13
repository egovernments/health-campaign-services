package org.digit.health.sync.orchestrator.client;

public interface SyncOrchestratorClient<I, O> {
    O orchestrate(I payload);
}
