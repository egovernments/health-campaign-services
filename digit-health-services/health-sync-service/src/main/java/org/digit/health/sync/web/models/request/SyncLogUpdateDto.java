package org.digit.health.sync.web.models.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncLogUpdateDto {
    private String syncId;
    private String status;
    private String tenantId;

}
