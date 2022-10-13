package org.digit.health.sync.orchestrator.client.metric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.digit.health.sync.orchestrator.client.enums.SyncLogStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SyncLogMetric {
    private SyncLogStatus syncLogStatus;
    private long totalCount;
    private long successCount;
    private long errorCount;
}
