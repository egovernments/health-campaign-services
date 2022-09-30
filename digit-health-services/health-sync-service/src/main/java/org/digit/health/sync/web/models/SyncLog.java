package org.digit.health.sync.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SyncLog {
	@JsonProperty("syncId")
	private String syncId;

	@JsonProperty("referenceId")
	private ReferenceId referenceId;

	@JsonProperty("tenantId")
	private String tenantId;

	@JsonProperty("fileDetails")
	private FileDetails fileDetails;

	@JsonProperty("status")
	private SyncStatus status;

	@JsonProperty("comment")
	private String comment;

	@JsonProperty("totalCount")
	private int totalCount;

	@JsonProperty("successCount")
	private int successCount;

	@JsonProperty("errorCount")
	private int errorCount;

	@JsonProperty("auditDetails")
	private AuditDetails auditDetails;
}