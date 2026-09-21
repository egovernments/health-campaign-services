package org.egov.common.models.idgen;

import org.egov.common.contract.request.RequestInfo;
import lombok.*;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IDPoolGenerationKafkaRequest {
    @NotNull
    private RequestInfo requestInfo;
    @NotNull
    private String tenantId;
    @Positive
    private Integer chunkSize;
}

