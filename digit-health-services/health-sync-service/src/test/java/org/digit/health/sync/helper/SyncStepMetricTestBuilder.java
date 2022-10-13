package org.digit.health.sync.helper;

import org.digit.health.sync.context.enums.RecordIdType;
import org.digit.health.sync.context.enums.StepSyncStatus;
import org.digit.health.sync.context.metric.SyncStepMetric;

public class SyncStepMetricTestBuilder {
    private SyncStepMetric.SyncStepMetricBuilder builder;

    public static SyncStepMetricTestBuilder builder() {
        return new SyncStepMetricTestBuilder();
    }

    public SyncStepMetricTestBuilder() {
        this.builder = SyncStepMetric.builder();
    }

    public SyncStepMetric build() {
        return builder.build();
    }

    public SyncStepMetricTestBuilder withCompletedRegistrationStep() {
        builder.status(StepSyncStatus.COMPLETED)
                .recordIdType(RecordIdType.REGISTRATION);
        return this;
    }

    public SyncStepMetricTestBuilder withCompletedDeliveryStep() {
        builder.status(StepSyncStatus.COMPLETED)
                .recordIdType(RecordIdType.DELIVERY);
        return this;
    }

    public SyncStepMetricTestBuilder withFailedDeliveryStep() {
        builder.status(StepSyncStatus.FAILED)
                .recordIdType(RecordIdType.DELIVERY);
        return this;
    }
}
