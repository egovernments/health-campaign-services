package org.digit.health.sync.context.step;

import org.digit.health.sync.context.SyncContext;
import org.digit.health.sync.context.metric.SyncMetric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Observable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SyncStepTest {

    @Mock
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("step should be able to publish sync metric and sync context should receive it")
    void testThatStepCanPublishSyncMetricAndSyncContextShouldReceiveIt() {
        SyncStep testStep = new TestStep();
        SyncContext syncContext = Mockito.spy(new TestSyncContext(testStep));
        testStep.addObserver(syncContext);

        testStep.handle("some-payload");

        verify(syncContext, times(1))
                .update(any(SyncStep.class), any(SyncMetric.class));
    }

    class TestStep extends SyncStep {

        @Override
        public void nextSyncStep(SyncContext syncContext) {

        }

        @Override
        public void handle(Object payload) {
            this.setChanged();
            this.notifyObservers(SyncMetric.builder().build());
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
        public List<SyncMetric> getSyncMetrics() {
            return null;
        }

        @Override
        public void update(Observable o, Object arg) {

        }
    }

}