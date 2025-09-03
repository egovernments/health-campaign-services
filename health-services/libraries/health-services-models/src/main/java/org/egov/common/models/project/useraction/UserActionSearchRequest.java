package org.egov.common.models.project.useraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.validation.annotation.Validated;

/**
 * The UserActionSearchRequest class is used to encapsulate the request information
 * for searching user actions. It includes the request metadata and the search criteria.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserActionSearchRequest {

    /**
     * The RequestInfo object contains metadata about the request, such as the
     * API version, request timestamp, and user details. This field is mandatory
     * and must be valid.
     */
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.request.RequestInfo requestInfo;

    /**
     * The UserAction object contains the search criteria for filtering user actions.
     * This includes various filters such as project IDs, beneficiary tags, resource tags,
     * and boundary codes. This field is mandatory and must be valid.
     */
    @JsonProperty("UserAction")
    @NotNull
    @Valid
    private UserActionSearch userAction;
}