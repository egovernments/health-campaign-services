package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * Condition
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Condition {

    @JsonProperty
    private String id = null;

    @JsonProperty("entity")
    @NotNull
    @Size(min = 2, max = 64)
    private String entity = null;

    @JsonProperty("entityProperty")
    @NotNull
    @Size(min = 2, max = 64)
    private String entityProperty = null;

    @JsonProperty("expression")
    @NotNull
    @Size(min = 3, max = 2048)
    private String expression = null;

}
