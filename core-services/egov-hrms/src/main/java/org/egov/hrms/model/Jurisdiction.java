package org.egov.hrms.model;

import lombok.*;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Validated
@EqualsAndHashCode(exclude = {"auditDetails"})
@Builder
@AllArgsConstructor
@Getter
@NoArgsConstructor
@Setter
@ToString
public class Jurisdiction {

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder().toFactory();

    private String id;

    @NotNull
    @Size(min = 2, max = 100)
    private String hierarchy;

    @NotNull
    @Size(min = 2, max = 100)
    private String boundary;

    @NotNull
    @Size(max = 256)
    private String boundaryType;

    private String tenantId;

    private AuditDetails auditDetails;

    private Boolean isActive;

    public void setId(String id) {
        this.id = sanitize(id);
    }

    public void setHierarchy(String hierarchy) {
        this.hierarchy = sanitize(hierarchy);
    }

    public void setBoundary(String boundary) {
        this.boundary = sanitize(boundary);
    }

    public void setBoundaryType(String boundaryType) {
        this.boundaryType = sanitize(boundaryType);
    }

    public void setTenantId(String tenantId) {
        this.tenantId = sanitize(tenantId);
    }

    private String sanitize(String input) {
        return input == null ? null : POLICY.sanitize(input);
    }
}
