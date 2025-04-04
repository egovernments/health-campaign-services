package org.egov.common.models.idgen;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
