package org.egov.workerregistry.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhotoSignatureUpdateRecord {

    @JsonProperty("individualId")
    private String individualId;

    @JsonProperty("photoId")
    private String photoId;

    @JsonProperty("signatureId")
    private String signatureId;

    @JsonProperty("tenantId")
    private String tenantId;
}
