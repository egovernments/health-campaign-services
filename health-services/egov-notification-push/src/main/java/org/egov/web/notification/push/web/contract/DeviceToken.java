package org.egov.web.notification.push.web.contract;

import jakarta.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;

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

	// HRUTHVIK: Added facilityId to link device tokens to a facility for facility-based push notifications
	private String facilityId;

	private AuditDetails auditDetails;

}
