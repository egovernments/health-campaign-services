package org.egov.common.models.project.useraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

/**
 * The UserActionResponse class is used for handling responses that involve a single user action.
 * It contains a ResponseInfo object and a UserAction object.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserActionResponse {

    /**
     * The ResponseInfo object containing metadata about the response.
     * This field is mandatory and must be valid.
     */
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    /**
     * The UserAction object representing the user action in the response.
     * This field is mandatory and must be valid.
     */
    @JsonProperty("UserAction")
    @NotNull
    @Valid
    private UserAction userAction = null;

}


