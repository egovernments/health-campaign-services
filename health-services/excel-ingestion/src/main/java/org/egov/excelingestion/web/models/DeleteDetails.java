package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeleteDetails {

    @JsonProperty("Message")
    private String message;

    @JsonProperty("TenantId")
    private String tenantId;

    @JsonProperty("ReferenceId")
    private String referenceId;

    @JsonProperty("FileStoreId")
    private String fileStoreId;
}