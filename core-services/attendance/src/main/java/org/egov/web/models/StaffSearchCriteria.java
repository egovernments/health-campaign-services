package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StaffSearchCriteria {

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("individualIds")
    private List<String> individualIds;

    @JsonProperty("registerIds")
    private List<String> registerIds;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("offset")
    private Integer offset;
}
