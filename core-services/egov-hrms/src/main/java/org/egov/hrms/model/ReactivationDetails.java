package org.egov.hrms.model;

import lombok.*;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
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
public class ReactivationDetails {

	private static final PolicyFactory POLICY = new HtmlPolicyBuilder().toFactory();

	private String id;

	@NotNull
	private String reasonForReactivation;

	private String orderNo;

	private String remarks;

	@NotNull
	private Long effectiveFrom;

	private String tenantId;

	private AuditDetails auditDetails;

	public void setId(String id) {
		this.id = sanitize(id);
	}

	public void setReasonForReactivation(String reasonForReactivation) {
		this.reasonForReactivation = sanitize(reasonForReactivation);
	}

	public void setOrderNo(String orderNo) {
		this.orderNo = sanitize(orderNo);
	}

	public void setRemarks(String remarks) {
		this.remarks = sanitize(remarks);
	}

	public void setTenantId(String tenantId) {
		this.tenantId = sanitize(tenantId);
	}

	private String sanitize(String input) {
		return input == null ? null : POLICY.sanitize(input);
	}
}
