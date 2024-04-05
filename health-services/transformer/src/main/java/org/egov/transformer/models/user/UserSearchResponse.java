package org.egov.transformer.models.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.egov.common.contract.response.ResponseInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;


@Setter
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class UserSearchResponse {

    @JsonProperty("ResponseInfo")
    private @NotNull @Valid ResponseInfo responseInfo = null;

    @JsonProperty("user")
    private @NotNull @Valid List<UserSearchResponseContent> userSearchResponseContent;


}
