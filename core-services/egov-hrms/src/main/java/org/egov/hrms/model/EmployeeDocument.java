package org.egov.hrms.model;

import lombok.*;
import org.egov.hrms.model.enums.EmployeeDocumentReferenceType;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.validation.annotation.Validated;

@Validated
@EqualsAndHashCode(exclude = {"auditDetails"})
@AllArgsConstructor
@Getter
@NoArgsConstructor
@Setter
@ToString
@Builder
public class EmployeeDocument {

	private static final PolicyFactory POLICY = new HtmlPolicyBuilder().toFactory();

	private String id;

	private String documentName;

	private String documentId;

	private EmployeeDocumentReferenceType referenceType;

	private String referenceId;

	private String tenantId;

	private AuditDetails auditDetails;

	public void setId(String id) {
		this.id = sanitize(id);
	}

	public void setDocumentName(String documentName) {
		this.documentName = sanitize(documentName);
	}

	public void setDocumentId(String documentId) {
		this.documentId = sanitize(documentId);
	}

	public void setReferenceId(String referenceId) {
		this.referenceId = sanitize(referenceId);
	}

	public void setTenantId(String tenantId) {
		this.tenantId = sanitize(tenantId);
	}

	private String sanitize(String input) {
		return input == null ? null : POLICY.sanitize(input);
	}
}
