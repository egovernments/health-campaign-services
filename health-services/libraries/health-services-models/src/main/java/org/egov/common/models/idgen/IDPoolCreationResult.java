package org.egov.common.models.idgen;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class IDPoolCreationResult {
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("message")
    private String message;
}

