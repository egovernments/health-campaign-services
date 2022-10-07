package org.digit.health.sync.context;

import org.digit.health.sync.context.enums.SyncErrorCode;
import org.digit.health.sync.context.metric.SyncMetric;
import org.digit.health.sync.context.step.SyncStep;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Observable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SyncContextTest {

    private static final String SOME_PAYLOAD = "some_payload";

    @Test
    @DisplayName("context should be able to transition to the next step when one is available")
    void testTransitionToNextStepWhenAvailable() {
        SyncContext syncContext = new TestSyncContext(new TestSyncStep());
        SyncStep syncStep = syncContext.getSyncStep();

        syncContext.nextSyncStep();
        SyncStep deliverySyncStep = syncContext.getSyncStep();

        assertNotSame(syncStep, deliverySyncStep);
    }

    @Test
    @DisplayName("context should be able to handle a payload as per current step")
    void testThatContextShouldBeAbleToHandleThePayloadAsPerCurrentStep() {
        TestSyncStep syncStep = Mockito.spy(new TestSyncStep());
        SyncContext syncContext = new TestSyncContext(syncStep);

        syncContext.handle(SOME_PAYLOAD);

        verify(syncStep, times(1))
                .handle(SOME_PAYLOAD);
    }

    @Test
    @DisplayName("context should throw an exception if one step is handled twice")
    void testThatContextThrowsAnExceptionOnHandlingOfOneStepTwice() {
        TestSyncStep syncStep = Mockito.spy(new TestSyncStep());
        SyncContext syncContext = new TestSyncContext(syncStep);

        syncContext.handle(SOME_PAYLOAD);
        try {
            syncContext.handle(SOME_PAYLOAD);
        } catch (CustomException cex) {
            assertEquals(SyncErrorCode.STEP_ALREADY_HANDLED.name(), cex.getCode());
            assertEquals(SyncErrorCode.STEP_ALREADY_HANDLED.message(syncContext
                    .getSyncStep().getClass()), cex.getMessage());
        }
        verify(syncStep, times(1))
                .handle(anyString());
    }

    @Test
    @DisplayName("context should return true if it has any step it can transition to next")
    void testThatContextShouldBeAbleToReturnTrueIfItHasNextStep() {
        SyncContext syncContext = new TestSyncContext(new TestSyncStep());

        syncContext.handle(SOME_PAYLOAD);

        assertTrue(syncContext.hasNext());
    }

    @Test
    @DisplayName("context should return false if it does not have any step it can transition to next")
    void testThatContextShouldBeAbleToReturnFalseIfItDoesNotHaveANextStep() {
        SyncContext syncContext = new TestSyncContext(new TestSyncStep());

        syncContext.nextSyncStep();

        assertFalse(syncContext.hasNext());
    }

    @Test
    @DisplayName("context should be able to provide metrics when available")
    void testThatContextCanProvideMetricsWhenAvailable() {
        TestSyncStep syncStep = new TestSyncStep();
        SyncContext syncContext = new TestSyncContext(syncStep);

        syncContext.handle(SOME_PAYLOAD);

        assertEquals(1, syncContext.getSyncMetrics().size());
    }

    static class TestSyncContext extends SyncContext {

        TestSyncContext(SyncStep syncStep) {
            super(syncStep);
        }

        @Override
        public void nextSyncStep() {
            syncStep.nextSyncStep(this);
        }

        @Override
        public SyncStep getSyncStep() {
            return this.syncStep;
        }

        @Override
        public void setSyncStep(SyncStep syncStep) {
            this.syncStep = syncStep;
        }

        @Override
        public void handle(Object payload) {
            throwExceptionIfAlreadyHandled();
            this.syncStep.handle(payload);
            markHandled();
        }

        @Override
        public boolean hasNext() {
            return this.syncStep.hasNext();
        }

        @Override
        public List<SyncMetric> getSyncMetrics() {
            return this.syncMetrics;
        }

        @Override
        public void update(Observable o, Object arg) {
            this.syncMetrics.add((SyncMetric) arg);
        }
    }

    static class TestSyncStep extends SyncStep {

        @Override
        public void nextSyncStep(SyncContext syncContext) {
            syncContext.setSyncStep(new SyncStep() {
                @Override
                public void nextSyncStep(SyncContext syncContext) {

                }

                @Override
                public void handle(Object payload) {

                }

                @Override
                public boolean hasNext() {
                    return false;
                }
            });
        }

        @Override
        public void handle(Object payload) {
            this.setChanged();
            this.notifyObservers(SyncMetric.builder().build());
        }

        @Override
        public boolean hasNext() {
            return true;
        }
    }
}
