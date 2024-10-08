package digit.web.models.hrms;


import javax.validation.constraints.NotNull;

import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.*;

@Validated
@EqualsAndHashCode(exclude = {"auditDetails"})
@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@Setter
@ToString
public class Assignment {

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

	private  String reportingTo;

	@JsonProperty("isHOD")
	private Boolean isHOD;
	
	@NotNull
	@JsonProperty("isCurrentAssignment")
	private Boolean isCurrentAssignment;

	private AuditDetails auditDetails;

}