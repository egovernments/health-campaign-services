package org.egov.common.models.project.useraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

/**
 * The UserActionRequest class is used for handling requests that involve a single user action.
 * It contains a RequestInfo object and a UserAction object.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserActionRequest {

    /**
     * The RequestInfo object containing metadata about the request.
     * This field is mandatory and must be valid.
     */
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo = null;

    /**
     * The UserAction object representing the user action in the request.
     * This field is mandatory and must be valid.
     */
    @JsonProperty("UserAction")
    @NotNull
    @Valid
    private UserAction userAction = null;
}

