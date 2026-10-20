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
public class MetricResponse {

        @Valid
        @JsonProperty("statusInfo")
        private StatusInfo statusInfo;

        @Valid
        @JsonProperty("responseData")
        private ResponseData responseData;

}
