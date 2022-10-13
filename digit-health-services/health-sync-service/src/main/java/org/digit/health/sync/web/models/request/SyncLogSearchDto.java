package org.digit.health.sync.web.models.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.digit.health.sync.web.models.ReferenceId;
import org.egov.common.contract.request.RequestInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncLogSearchDto {

    private RequestInfo requestInfo;
    private String tenantId;
    private String syncId;
    private String status;
    private ReferenceId reference;
    private String fileStoreId;

}
