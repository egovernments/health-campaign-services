package org.egov.common.models.referralmanagement.beneficiarydownsync;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownsyncCriteria {

	@NotNull
	private String locality;
	
	private Long lastSyncedTime;
	
	@NotNull
	private String projectId;
	
	@NotNull
	private String tenantId;
	
	@Default
	private Boolean includeDeleted = false;
	
	@Default
	private Integer offset = 0;
	
	@Default
	private Integer limit = 50;
	
	private Long totalCount;
}

