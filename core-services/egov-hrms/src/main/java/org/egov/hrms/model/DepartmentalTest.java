package org.egov.hrms.model;

import lombok.*;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

@Validated
@EqualsAndHashCode(exclude = {"auditDetails"})
@Builder
@AllArgsConstructor
@Getter
@NoArgsConstructor
@Setter
@ToString
public class DepartmentalTest {

	private static final PolicyFactory POLICY = new HtmlPolicyBuilder().toFactory();

	private String id;

	@NotNull
	private String test;

	@NotNull
	private Long yearOfPassing;

	private String remarks;

	private String tenantId;

	private AuditDetails auditDetails;

	private Boolean isActive;

	public void setId(String id) {
		this.id = sanitize(id);
	}

	public void setTest(String test) {
		this.test = sanitize(test);
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
