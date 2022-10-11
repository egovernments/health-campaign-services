package org.digit.health.sync.context.metric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.digit.health.sync.context.enums.RecordIdType;
import org.digit.health.sync.context.enums.StepSyncStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SyncStepMetric {
    private StepSyncStatus status;
    private String recordId;
    private RecordIdType recordIdType;
    private String errorCode;
    private String errorMessage;
}
