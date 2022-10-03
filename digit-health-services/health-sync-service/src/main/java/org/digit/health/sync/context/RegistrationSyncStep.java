package org.digit.health.sync.context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component("registrationSyncStep")
public class RegistrationSyncStep implements SyncStep {

    private final ApplicationContext applicationContext;

    @Autowired
    public RegistrationSyncStep(ApplicationContext applicationContext) {

        this.applicationContext = applicationContext;
    }

    @Override
    public void nextSyncStep(SyncContext syncContext) {
        syncContext.setSyncStep(applicationContext.getBean(DeliverySyncStep.class));
    }

    @Override
    public void handle(Object payload) {
        // TODO: Implementation
    }

    @Override
    public boolean hasNext() {
        return true;
    }
}
