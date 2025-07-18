package org.egov.hrms.model;

import lombok.*;
import org.egov.tracer.annotations.CustomSafeHtml;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@Validated
@EqualsAndHashCode(exclude = {"auditDetails"})
@AllArgsConstructor
@Getter
@NoArgsConstructor
@Setter
@ToString
@Builder
public class ReactivationDetails {

	@CustomSafeHtml
	private String id;

	@CustomSafeHtml
	@NotNull
	private String reasonForReactivation;

	@CustomSafeHtml
	private String orderNo;

	@CustomSafeHtml
	private String remarks;

	@NotNull
	private Long effectiveFrom;

	@CustomSafeHtml
	private String tenantId;

	private AuditDetails auditDetails;
}
