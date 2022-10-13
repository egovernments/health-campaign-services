package org.digit.health.sync.context.step;

import org.digit.health.sync.context.SyncContext;
import org.digit.health.sync.context.metric.SyncStepMetric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Observable;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class SyncStepTest {

    @Mock
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("step should be able to publish sync metric and sync context should receive it")
    void testThatStepCanPublishSyncMetricAndSyncContextShouldReceiveIt() throws InterruptedException {
        SyncStep testStep = new TestStep();
        SyncContext syncContext = new TestSyncContext(testStep);

        testStep.handle("some-payload");

        assertEquals(1, syncContext.getSyncMetrics().size());
    }

    static class TestStep extends SyncStep {

        @Override
        public void nextSyncStep(SyncContext syncContext) {

        }

        @Override
        public void handle(Object payload) {
            this.setChanged();
            this.notifyObservers(SyncStepMetric.builder().build());
        }

        @Override
        public boolean hasNext() {
            return false;
        }
    }

    static class TestSyncContext extends SyncContext {

        TestSyncContext(SyncStep syncStep) {
            super(syncStep);
        }

        @Override
        public void nextSyncStep() {

        }

        @Override
        public SyncStep getSyncStep() {
            return null;
        }

        @Override
        public void setSyncStep(SyncStep syncStep) {

        }

        @Override
        public void handle(Object payload) {

        }

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public List<SyncStepMetric> getSyncMetrics() {
            return this.syncStepMetrics;
        }

        @Override
        public void update(Observable o, Object arg) {
            this.syncStepMetrics.add((SyncStepMetric) arg);
        }
    }

}