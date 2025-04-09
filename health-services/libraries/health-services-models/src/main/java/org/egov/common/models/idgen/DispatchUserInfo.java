package org.egov.common.models.idgen;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DispatchUserInfo {

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId;

    @JsonProperty("userUuid")
    @NotNull
    private String userUuid;

    @JsonProperty("count")
    @NotNull
    private int count;

    @JsonProperty("deviceUuid")
    @NotNull
    private String deviceUuid;

    @JsonProperty("deviceInfo")
    @NotNull
    private String deviceInfo;
}
