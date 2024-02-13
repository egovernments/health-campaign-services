package org.egov.transformer.models.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter

public class UserSearchResponse {

    @JsonProperty("ResponseInfo")
    private @NotNull @Valid ResponseInfo responseInfo = null;

    @JsonProperty("user")
    private @NotNull @Valid List<UserSearchResponseContent> userSearchResponseContent;
    public ResponseInfo getResponseInfo() {
        return responseInfo;
    }

    public void setResponseInfo(ResponseInfo responseInfo) {
        this.responseInfo = responseInfo;
    }


    public List<UserSearchResponseContent> getUserSearchResponseContent() {
        return userSearchResponseContent;
    }

    public void setUserSearchResponseContent(List<UserSearchResponseContent> userSearchResponseContent) {
        this.userSearchResponseContent = userSearchResponseContent;
    }

}
