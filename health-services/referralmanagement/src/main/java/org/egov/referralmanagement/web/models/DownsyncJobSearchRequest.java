package org.egov.referralmanagement.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

/**
 * Search request for {@code /downsync/v1/jobs/_search}. Both {@code jobId}
 * and {@code tenantId} are required — the tenantId is what lets the
 * repository resolve the right schema in one shot (via
 * {@code MultiStateInstanceUtil.replaceSchemaPlaceholder}) instead of
 * scanning every tenant schema on the pod. A search that omitted tenantId
 * would either need cross-schema iteration (expensive on central-instance
 * deployments) or would silently return 404 whenever the {@code SCHEMA_NAME}
 * env var was misconfigured.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownsyncJobSearchRequest {
    @JsonProperty("RequestInfo")
    private RequestInfo requestInfo;

    @NotBlank(message = "jobId is required")
    private String jobId;

    @NotBlank(message = "tenantId is required")
    private String tenantId;
}
