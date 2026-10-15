package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * EnrichedBoundary
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EnrichedBoundary {

    @JsonProperty("id")
    private String id;

    @JsonProperty("code")
    @NotNull
    private String code;

    @JsonProperty("boundaryType")
    private String boundaryType;

    @JsonProperty("children")
    @Valid
    private List<EnrichedBoundary> children = null;

    @JsonIgnore
    private String parent = null;

}