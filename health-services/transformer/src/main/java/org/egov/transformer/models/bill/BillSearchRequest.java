package org.egov.transformer.models.bill;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.validation.annotation.Validated;

/**
 * BillSearchRequest
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-04-02T17:49:59.877+05:30[Asia/Kolkata]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillSearchRequest {
	
	@JsonProperty("RequestInfo")
	@NotNull
	private RequestInfo requestInfo;

	@JsonProperty("billCriteria")
	@NotNull
	@Valid
	private BillCriteria billCriteria;

	@JsonProperty("pagination")
	@NotNull
	@Default
	private Pagination pagination = new Pagination();

}
