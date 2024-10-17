package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Resource
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Resource {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("resourceType")
    @NotNull
    @Size(min = 2, max = 256)
    private String resourceType = null;

    @JsonProperty("estimatedNumber")
    @NotNull
    private BigDecimal estimatedNumber = null;

    @JsonProperty("activityCode")
    @Size(min = 2, max = 128)
    private String activityCode = null;

}
