package org.egov.inbox.web.model.dss;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class InboxMetricCriteria {

    @Valid
    @JsonProperty("tenantId")
    private String tenantId;

    @Valid
    @JsonProperty("module")
    private String module;
}
