package org.digit.health.sync.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SyncOrchestratorTest {

    @Test
    @DisplayName("sync orchestrator should be able to orchestrate and return a result")
    void testThatSyncOrchestratorCanOrchestrateByTakingAnObjectAndReturnsAResult() {
        SyncOrchestrator orchestrator = new TestSyncOrchestrator();

        Object result = orchestrator.orchestrate(new Object());

        assertNotNull(result);
    }

    static class TestSyncOrchestrator implements SyncOrchestrator {

        @Override
        public Object orchestrate(Object param) {
            return new Object();
        }
    }
}
