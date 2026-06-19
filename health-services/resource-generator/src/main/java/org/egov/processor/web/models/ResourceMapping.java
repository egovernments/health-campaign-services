package org.egov.processor.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * ResourceMapping
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResourceMapping {
    @JsonProperty("id")
    @Valid
    private String id = null;

    @JsonProperty("filestoreId")
    @NotNull
    @Size(min = 1, max = 128)
    @Pattern(regexp = "^(?!\\p{Punct}+$).*$", message = "Filestore Id must not contain only special characters")
    private String filestoreId = null;

    @JsonProperty("mappedFrom")
    @NotNull
    @Size(min = 2, max = 256)
    private String mappedFrom = null;

    @JsonProperty("mappedTo")
    @NotNull
    @Size(min = 2, max = 256)
    private String mappedTo = null;

    @JsonProperty("active")
    @NotNull
    private Boolean active = true;

}
