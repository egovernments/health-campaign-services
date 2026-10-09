package org.egov.excelingestion.web.models.localization;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LocalisationSearchCriteria {
    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("module")
    private String module;

    @JsonProperty("locale")
    private String locale;

    @JsonProperty("codes")
    private String codes; // Assuming a comma-separated string of codes if needed
}
