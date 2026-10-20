package org.egov.excelingestion.web.models;

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
public class BoundaryHierarchyChild {

    @JsonProperty("boundaryType")
    private String boundaryType;

    @JsonProperty("parentBoundaryType")
    private String parentBoundaryType;

    @JsonProperty("active")
    private Boolean active;

}