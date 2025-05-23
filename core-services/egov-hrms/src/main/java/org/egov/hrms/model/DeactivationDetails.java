package org.egov.hrms.model;

import lombok.*;
import org.egov.tracer.annotations.CustomSafeHtml;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

@Validated
@EqualsAndHashCode(exclude = {"auditDetails"})
@AllArgsConstructor
@Getter
@NoArgsConstructor
@Setter
@ToString
@Builder
public class DeactivationDetails {

	@CustomSafeHtml
	private String id;

	@CustomSafeHtml
	@NotNull
	private String reasonForDeactivation;

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
