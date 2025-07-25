package org.egov.common.models.idgen;

import org.egov.common.contract.request.RequestInfo;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IDPoolGenerationKafkaRequest {
    private RequestInfo requestInfo;
    private String tenantId;
    private Integer chunkSize;
}

