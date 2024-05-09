package org.egov.transformer.models.boundary;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * EnrichedBoundary
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-10-16T17:02:11.361704+05:30[Asia/Kolkata]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EnrichedBoundary {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("code")
    @NotNull
    private String code = null;

    @JsonProperty("boundaryType")
    private String boundaryType = null;

    @JsonProperty("children")
    @Valid
    private List<EnrichedBoundary> children = null;

    @JsonIgnore
    private String parent = null;

}
