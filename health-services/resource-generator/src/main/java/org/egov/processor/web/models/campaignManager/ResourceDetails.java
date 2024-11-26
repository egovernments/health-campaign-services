package org.egov.processor.web.models.campaignManager;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResourceDetails {

    @JsonProperty("type")
    @NotNull
    @Size(min = 1, max = 64)
    private String type;

    @JsonProperty("hierarchyType")
    @NotNull
    @Size(min = 1, max = 64)
    private String hierarchyType;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId;

    @JsonProperty("fileStoreId")
    @NotNull
    private String fileStoreId;

    @JsonProperty("action")
    @Size(min = 1, max = 64)
    private String action;

    @JsonProperty("campaignId")
    private String campaignId;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

}
