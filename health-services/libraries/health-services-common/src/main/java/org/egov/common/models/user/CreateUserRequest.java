package org.egov.common.models.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@AllArgsConstructor
@Getter
@NoArgsConstructor
public class CreateUserRequest {
    private RequestInfo requestInfo;

    @NotNull
    @Valid
    private UserRequest user;

    public User toDomain(boolean isCreate) {
        return user.toDomain(loggedInUserId(), isCreate);
    }

    // TODO Update libraries to have uuid in request info
    private Long loggedInUserId() {
        return requestInfo.getUserInfo() == null ? null : requestInfo.getUserInfo().getId();
    }

}


