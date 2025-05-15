package org.egov.hrms.model;

import com.google.common.html.HtmlEscapers;
import lombok.*;
import org.egov.hrms.web.contract.User;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

@Validated
@AllArgsConstructor
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Setter
@ToString
@Builder
public class Employee {

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder().toFactory();

    private Long id;

    @Size(max = 1024)
    private String uuid;

    @Size(min = 1, max = 256)
    private String code;

    @Size(max = 250)
    private String employeeStatus;

    @NotNull
    @Size(max = 250)
    private String employeeType;

    private Long dateOfAppointment;

    @Valid
    @NotEmpty
    @Size(min = 1, max = 50)
    private List<Jurisdiction> jurisdictions = new ArrayList<>();

    @Valid
    private List<Assignment> assignments = new ArrayList<>();

    @Valid
    @Size(max = 25)
    private List<ServiceHistory> serviceHistory = new ArrayList<>();

    private Boolean IsActive;

    @Valid
    @Size(max = 25)
    private List<EducationalQualification> education = new ArrayList<>();

    @Valid
    @Size(max = 25)
    private List<DepartmentalTest> tests = new ArrayList<>();

    @NotNull
    @Size(max = 250)
    private String tenantId;

    @Valid
    @Size(max = 50)
    private List<EmployeeDocument> documents = new ArrayList<>();

    @Valid
    private List<DeactivationDetails> deactivationDetails = new ArrayList<>();

    private List<ReactivationDetails> reactivationDetails = new ArrayList<>();

    private AuditDetails auditDetails;

    private Boolean reActivateEmployee;

    @Valid
    @NotNull
    private User user;

    public void setUuid(String uuid) {
        this.uuid = sanitize(uuid);
    }

    public void setCode(String code) {
        this.code = sanitize(code);
    }

    public void setEmployeeStatus(String employeeStatus) {
        this.employeeStatus = sanitize(employeeStatus);
    }

    public void setEmployeeType(String employeeType) {
        this.employeeType = sanitize(employeeType);
    }

    public void setTenantId(String tenantId) {
        this.tenantId = sanitize(tenantId);
    }

    private String sanitize(String input) {
        return input == null ? null : POLICY.sanitize(input);
    }
}
