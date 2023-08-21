package org.egov.transformer.models.upstream;

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
public class DeliveryConfiguration {
    @JsonProperty("programMandateValue")
    private Integer programMandateValue;

    @JsonProperty("dividingFactor")
    private Double dividingFactor;
}
