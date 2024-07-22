package org.egov.common.models.project.irs;

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
* UserActionBulkResponse
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserActionBulkResponse {

    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    @JsonProperty("UserActions")
    @NotNull
    @Valid
    private List<UserAction> userActions = null;

    public UserActionBulkResponse addUserAction(UserAction userActionItem) {
        if (this.userActions == null) {
            this.userActions = new ArrayList<>();
        }
        this.userActions.add(userActionItem);
        return this;
    }

}

