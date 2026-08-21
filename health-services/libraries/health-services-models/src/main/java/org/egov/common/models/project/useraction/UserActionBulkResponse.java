package org.egov.common.models.project.useraction;

import java.util.ArrayList;
import java.util.List;

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
 * The UserActionBulkResponse class is used for handling bulk responses of user actions.
 * It contains a ResponseInfo object, a total count of user actions, and a list of UserAction objects.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserActionBulkResponse {

    /**
     * The ResponseInfo object containing metadata about the response.
     * This field is mandatory and must be valid.
     */
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    /**
     * The total count of user actions in the response.
     * It is initialized to 0 by default.
     */
    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    /**
     * A list of UserAction objects that are part of the bulk response.
     * This field is mandatory and must be valid.
     */
    @JsonProperty("UserActions")
    @NotNull
    @Valid
    private List<UserAction> userActions = null;

    /**
     * Adds a UserAction item to the list of user actions in the bulk response.
     * This method is useful for incrementally building the list of user actions.
     *
     * @param userActionItem The UserAction object to be added to the list.
     * @return The current instance of UserActionBulkResponse with the new UserAction added.
     */
    public UserActionBulkResponse addUserAction(UserAction userActionItem) {
        if (this.userActions == null) {
            this.userActions = new ArrayList<>();
        }
        this.userActions.add(userActionItem);
        return this;
    }
}
