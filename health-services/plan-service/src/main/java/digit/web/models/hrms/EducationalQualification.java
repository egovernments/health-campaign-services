package digit.web.models.hrms;


import javax.validation.constraints.NotNull;


import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Validated
@EqualsAndHashCode(exclude = {"auditDetails"})
@Builder
@AllArgsConstructor
@Getter
@NoArgsConstructor
@Setter
@ToString
public class EducationalQualification {
	private String id;

	@NotNull
	private String qualification;

	@NotNull
	private String stream;

	@NotNull
	private Long yearOfPassing;

	private String university;

	private  String remarks;
	
	private  String tenantId;

	private AuditDetails auditDetails;

	private Boolean isActive;


}