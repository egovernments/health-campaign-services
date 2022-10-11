package org.digit.health.sync.orchestrator.client;

import org.digit.health.sync.orchestrator.SyncOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SyncOrchestratorClientTest {

    @Mock
    private SyncOrchestrator syncOrchestrator;

    @Test
    @DisplayName("orchestrator client should be able to call the orchestrator with required params")
    void testThatOrchestratorClientShouldCallTheOrchestratorWithRequiredParams() {
        SyncOrchestratorClient syncOrchestratorClient =
                new TestSyncOrchestratorClient(syncOrchestrator);

        Object payload = new Object();

        syncOrchestratorClient.orchestrate(payload);

        verify(syncOrchestrator, times(1)).orchestrate(payload);
    }

    @Component
    class TestSyncOrchestratorClient implements SyncOrchestratorClient {


        private final SyncOrchestrator syncOrchestrator;

        @Autowired
        public TestSyncOrchestratorClient(SyncOrchestrator syncOrchestrator) {

            this.syncOrchestrator = syncOrchestrator;
        }

        @Override
        public Object orchestrate(Object payload) {
            return syncOrchestrator.orchestrate(payload);
        }
    }
}
