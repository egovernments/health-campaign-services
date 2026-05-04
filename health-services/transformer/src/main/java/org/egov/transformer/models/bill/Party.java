package org.egov.transformer.models.bill;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.tracer.model.AuditDetails;
import org.springframework.validation.annotation.Validated;

/**
 * Account details
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-04-02T17:49:59.877+05:30[Asia/Kolkata]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Party {

	@JsonProperty("id")
	@Valid
	private String id;

	@JsonProperty("parentId")
	@Valid
	private String parentId;

	@JsonProperty("tenantId")
	@NotNull
	@Size(min = 2, max = 64)
	private String tenantId;
	
	@JsonProperty("type")
	@NotNull
	@Size(min = 2, max = 64)
	private String type;

	@JsonProperty("identifier")
	@NotNull
	@Size(min = 2, max = 64)
	private String identifier;

	@JsonProperty("paymentProvider")
	@Size(max = 16)
	private String paymentProvider;

	@JsonProperty("payeeName")
	@Size(max = 256)
	private String payeeName;

	@JsonProperty("payeePhoneNumber")
	@Size(max = 64)
	private String payeePhoneNumber;

	@JsonProperty("bankAccount")
	@Size(max = 128)
	private String bankAccount;

	@JsonProperty("bankCode")
	@Size(max = 64)
	private String bankCode;

	@JsonProperty("beneficiaryCode")
	@Size(max = 128)
	private String beneficiaryCode;

	@JsonProperty("status")
	private Status status;

	@JsonProperty("additionalDetails")
	private Object additionalDetails;

	@JsonProperty("auditDetails")
	@Valid
	private AuditDetails auditDetails;

}




