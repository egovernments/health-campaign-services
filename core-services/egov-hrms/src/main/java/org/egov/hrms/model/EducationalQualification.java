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
public class EducationalQualification {

	private static final PolicyFactory POLICY = new HtmlPolicyBuilder().toFactory();

	private String id;

	@NotNull
	private String qualification;

	@NotNull
	private String stream;

	@NotNull
	private Long yearOfPassing;

	private String university;

	private String remarks;

	private String tenantId;

	private AuditDetails auditDetails;

	private Boolean isActive;

	public void setId(String id) {
		this.id = sanitize(id);
	}

	public void setQualification(String qualification) {
		this.qualification = sanitize(qualification);
	}

	public void setStream(String stream) {
		this.stream = sanitize(stream);
	}

	public void setUniversity(String university) {
		this.university = sanitize(university);
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
