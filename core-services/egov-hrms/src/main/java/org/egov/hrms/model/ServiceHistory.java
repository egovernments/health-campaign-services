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
public class ServiceHistory {

	private static final PolicyFactory POLICY = new HtmlPolicyBuilder().toFactory();

	private String id;

	private String serviceStatus;

	private Long serviceFrom;

	private Long serviceTo;

	private String orderNo;

	private String location;

	private String tenantId;

	private Boolean isCurrentPosition;

	private AuditDetails auditDetails;

	public void setId(String id) {
		this.id = sanitize(id);
	}

	public void setServiceStatus(String serviceStatus) {
		this.serviceStatus = sanitize(serviceStatus);
	}

	public void setOrderNo(String orderNo) {
		this.orderNo = sanitize(orderNo);
	}

	public void setLocation(String location) {
		this.location = sanitize(location);
	}

	public void setTenantId(String tenantId) {
		this.tenantId = sanitize(tenantId);
	}

	private String sanitize(String input) {
		return input == null ? null : POLICY.sanitize(input);
	}
}
