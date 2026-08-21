package org.egov.common.models.idgen;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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


    @JsonProperty("totalCount")
    @NotNull
    @Positive
    private Integer totalCount;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId;
}
