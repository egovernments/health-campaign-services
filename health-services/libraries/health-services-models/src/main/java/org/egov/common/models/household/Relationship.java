package org.egov.common.models.household;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;
import org.springframework.validation.annotation.Validated;

/**
 * A representation of relationship with the relative
 * ie. Self - Ram, relative - Shyam, relationship type - father
 * then Shyam is the father of ram
 */
@ApiModel(description = "A representation of relationship with the relative")
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Relationship extends EgovOfflineModel
{

    @JsonProperty("selfId")
    @Size(min = 2, max = 64)
    private String selfId = null;

    @JsonProperty("selfClientReferenceId")
    @Size(min = 2, max = 64)
    private String selfClientReferenceId = null;

    @JsonProperty("relativeId")
    @Size(min = 2, max = 64)
    private String relativeId = null;

    @JsonProperty("relativeClientReferenceId")
    @Size(min = 2, max = 64)
    private String relativeClientReferenceId = null;

    @Size(min = 2, max = 64)
    @JsonProperty("relationshipType")
    @NotNull
    private String relationshipType;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;
}
