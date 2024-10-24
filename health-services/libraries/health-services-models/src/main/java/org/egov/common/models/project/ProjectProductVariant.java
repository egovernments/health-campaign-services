package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
* ProjectProductVariant
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectProductVariant{

    @JsonProperty("productVariantId")
    @NotNull
    private String productVariantId = null;

    @JsonProperty("type")
    private String type = null;

    @JsonProperty("isBaseUnitVariant")
    private Boolean isBaseUnitVariant = null;

}

