package org.digit.health.sync.orchestrator.client;

public interface SyncOrchestratorClient {
    Object orchestrate(Object payload);
}
