package org.egov.common.models.project.useraction;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

/**
 * The UserActionBulkRequest class is used for handling bulk requests of user actions.
 * It contains a RequestInfo object and a list of UserAction objects.
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserActionBulkRequest {

    /**
     * The RequestInfo object containing metadata about the request.
     * This field is mandatory and must be valid.
     */
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private RequestInfo requestInfo = null;

    /**
     * A list of UserAction objects that are part of the bulk request.
     * This field is mandatory, must contain at least one item, and must be valid.
     * It is initialized to an empty list by default.
     */
    @JsonProperty("UserActions")
    @NotNull
    @Valid
    @Size(min = 1)
    @Builder.Default
    private List<UserAction> userActions = new ArrayList<>();

    /**
     * Adds a UserAction item to the list of user actions in the bulk request.
     * This method is useful for incrementally building the list of user actions.
     *
     * @param userAction The UserAction object to be added to the list.
     * @return The current instance of UserActionBulkRequest with the new UserAction added.
     */
    public UserActionBulkRequest addTaskItem(UserAction userAction) {
        this.userActions.add(userAction);
        return this;
    }
}
