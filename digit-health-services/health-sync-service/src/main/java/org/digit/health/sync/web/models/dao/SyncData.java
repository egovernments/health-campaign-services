package org.digit.health.sync.web.models.dao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncData {

    @JsonProperty("syncId")
    private String id;

    @JsonProperty("referenceId")
    private String referenceId;

    @JsonProperty("referenceIdType")
    private String referenceIdType;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @JsonProperty("checksum")
    private String checksum;

    @JsonProperty("status")
    private String status;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("totalCount")
    private int totalCount;

    @JsonProperty("successCount")
    private int successCount;

    @JsonProperty("errorCount")
    private int errorCount;

    @JsonProperty("createdBy")
    private String createdBy;

    @JsonProperty("lastModifiedBy")
    private String lastModifiedBy;

    @JsonProperty("createdTime")
    private Long createdTime;

    @JsonProperty("lastModifiedTime")
    private Long lastModifiedTime;

}
