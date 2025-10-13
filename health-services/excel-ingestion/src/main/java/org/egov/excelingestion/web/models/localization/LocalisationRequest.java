package org.egov.excelingestion.web.models.localization;

import org.egov.excelingestion.web.models.RequestInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LocalisationRequest {
    @JsonProperty("RequestInfo")
    private RequestInfo requestInfo;

    @JsonProperty("LocalisationSearchCriteria")
    private LocalisationSearchCriteria localisationSearchCriteria;
}
