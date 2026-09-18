package org.egov.transformer.models.boundary;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Validated

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundarySearchCriteria {

    @NotNull
    @Size(min = 1)
    @JsonProperty("codes")
    private List<String> codes;

    @NotNull
    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("offset")
    private Integer offset;

    @JsonProperty("limit")
    private Integer limit;

}
