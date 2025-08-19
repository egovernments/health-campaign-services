package org.egov.common.models.individual;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovModel;
import org.springframework.validation.annotation.Validated;

/**
 * A representation of an ABHA transaction for an Individual.
 */
@ApiModel(description = "A representation of an ABHA transaction for an Individual.")
@Validated

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AbhaTransaction extends EgovModel {


    @JsonProperty("individualId")
    @Size(min = 2, max = 64)
    private String individualId = null;

    @JsonProperty("transactionId")
    @Size(max = 255)
    private String transactionId = null;

    @JsonProperty("abhaNumber")
    @Size(min = 2, max = 64)
    private String abhaNumber = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;
}
