package org.egov.common.models.idgen;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jdk.jfr.BooleanFlag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ClientInfo {

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId;

    @JsonProperty("count")
    @NotNull
    @PositiveOrZero
    private int count;

    @JsonProperty("deviceUuid")
    @NotNull
    private String deviceUuid;

    @JsonProperty("deviceInfo")
    @NotNull
    private String deviceInfo;

    @JsonProperty("fetchAllocatedIds")
    @BooleanFlag
    private Boolean fetchAllocatedIds;
}
