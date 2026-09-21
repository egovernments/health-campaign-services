package org.egov.excelingestion.web.models.filestore;

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
public class FileInfo {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("fileName")
    private String fileName;

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @JsonProperty("contentType")
    private String contentType;

    @JsonProperty("module")
    private String module;

    @JsonProperty("tag")
    private String tag;

    @JsonProperty("auditDetails")
    private Object auditDetails;

    @JsonProperty("additionalDetails")
    private Object additionalDetails;

    @JsonProperty("url")
    private String url;
}