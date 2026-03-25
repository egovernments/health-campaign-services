package org.egov.web.notification.push.web.contract;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Validated
@AllArgsConstructor
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Setter
@ToString
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceToken {

	private String id;

	@NotNull
	private String userId;

	@NotNull
	private String deviceToken;

	@NotNull
	private String deviceType;

	@NotNull
	private String tenantId;

	// Internal field used by persister (one row per facilityId)
	private String facilityId;

	// API field — accepts/returns multiple facilityIds per device token
	private List<String> facilityIds;

	private AuditDetails auditDetails;

}
