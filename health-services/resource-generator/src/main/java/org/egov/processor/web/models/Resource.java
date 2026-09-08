package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

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
    private BigDecimal estimatedNumber = null;

    @JsonProperty("activityCode")
    @Size(min = 2, max = 128)
    private String activityCode = null;

}
