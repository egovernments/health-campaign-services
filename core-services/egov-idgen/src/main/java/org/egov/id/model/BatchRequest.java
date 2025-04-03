package org.egov.id.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BatchRequest {


    @JsonProperty("batchSize")
    private Integer BatchSize;

    @JsonProperty("tenantId")
    private String TenantId;
}
