package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

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
