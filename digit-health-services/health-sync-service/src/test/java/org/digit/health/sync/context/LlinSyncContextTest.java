package org.digit.health.sync.context;

import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlinSyncContextTest {

    @Mock
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("context should be able to transition to the delivery step when context is on registration step")
    void testTransitionToDeliveryStepWhenCurrentStepIsRegistrationStep() {
        SyncStep registrationSyncStep = new RegistrationSyncStep(applicationContext);
        SyncContext syncContext = new LlinSyncContext(registrationSyncStep);
        SyncStep syncStep = syncContext.getSyncStep();

        syncContext.nextSyncStep();
        SyncStep deliverySyncStep = syncContext.getSyncStep();

        assertNotSame(syncStep, deliverySyncStep);
    }

    @Test
    @DisplayName("context should be able to handle a payload as per current step")
    void testThatContextShouldBeAbleToHandleThePayloadAsPerCurrentStep() {
        SyncStep registrationSyncStep = Mockito.spy(new RegistrationSyncStep(applicationContext));
        SyncContext syncContext = new LlinSyncContext(registrationSyncStep);

        syncContext.handle("some_payload");

        verify(registrationSyncStep, times(1))
                .handle("some_payload");
    }

    @Test
    @DisplayName("context should throw an exception if one step is handled twice")
    void testThatContextThrowsAnExceptionOnHandlingOfOneStepTwice() {
        SyncStep registrationSyncStep = Mockito.spy(new RegistrationSyncStep(applicationContext));
        SyncContext syncContext = new LlinSyncContext(registrationSyncStep);

        syncContext.handle("some_payload");
        try {
            syncContext.handle("another_payload");
        } catch (CustomException cex) {
            assertEquals(SyncErrorCode.STEP_ALREADY_HANDLED.name(), cex.getCode());
            assertEquals(SyncErrorCode.STEP_ALREADY_HANDLED.message(syncContext
                    .getSyncStep().getClass()), cex.getMessage());
        }
        verify(registrationSyncStep, times(1))
                .handle("some_payload");
    }

    @Test
    @DisplayName("context should return true if it has any step it can transition to next")
    void testThatContextShouldBeAbleToReturnTrueIfItHasNextStep() {
        SyncStep registrationSyncStep = new RegistrationSyncStep(applicationContext);
        SyncContext syncContext = new LlinSyncContext(registrationSyncStep);

        syncContext.handle("some_payload");

        assertTrue(syncContext.hasNext());
    }

    @Test
    @DisplayName("context should return false if it does not have any step it can transition to next")
    void testThatContextShouldBeAbleToReturnFalseIfItDoesNotHaveANextStep() {
        SyncStep registrationSyncStep = new RegistrationSyncStep(applicationContext);
        SyncContext syncContext = new LlinSyncContext(registrationSyncStep);
        when(applicationContext.getBean(DeliverySyncStep.class))
                .thenReturn(new DeliverySyncStep(applicationContext));

        while(syncContext.hasNext()) {
            syncContext.nextSyncStep();
        }

        assertFalse(syncContext.hasNext());
    }
}
