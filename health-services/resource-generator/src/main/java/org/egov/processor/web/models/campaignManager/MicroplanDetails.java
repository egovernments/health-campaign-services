package org.egov.processor.web.models.campaignManager;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Validated
public class MicroplanDetails {

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId;

    @JsonProperty("campaignId")
    @NotNull
    private String campaignId;

    @JsonProperty("planConfigurationId")
    @NotNull
    private String planConfigurationId;

    @JsonProperty("resourceFilestoreId")
    private String resourceFilestoreId;
}