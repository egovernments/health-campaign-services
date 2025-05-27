package org.egov.hrms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

@Validated
@EqualsAndHashCode(exclude = {"auditDetails"})
@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@Setter
@ToString
public class Assignment {

	private static final PolicyFactory POLICY = new HtmlPolicyBuilder().toFactory();

	private String id;

	private Long position;

	@NotNull
	private String designation;

	@NotNull
	private String department;

	@NotNull
	private Long fromDate;

	private Long toDate;

	private String govtOrderNumber;

	private String tenantid;

	private String reportingTo;

	@JsonProperty("isHOD")
	private Boolean isHOD = false;

	@NotNull
	@JsonProperty("isCurrentAssignment")
	private Boolean isCurrentAssignment;

	private AuditDetails auditDetails;

	public void setId(String id) {
		this.id = sanitize(id);
	}

	public void setDesignation(String designation) {
		this.designation = sanitize(designation);
	}

	public void setDepartment(String department) {
		this.department = sanitize(department);
	}

	public void setGovtOrderNumber(String govtOrderNumber) {
		this.govtOrderNumber = sanitize(govtOrderNumber);
	}

	public void setTenantid(String tenantid) {
		this.tenantid = sanitize(tenantid);
	}

	public void setReportingTo(String reportingTo) {
		this.reportingTo = sanitize(reportingTo);
	}

	private String sanitize(String input) {
		return input == null ? null : POLICY.sanitize(input);
	}
}
