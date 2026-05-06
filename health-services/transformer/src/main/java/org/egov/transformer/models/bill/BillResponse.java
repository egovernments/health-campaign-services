package org.egov.transformer.models.bill;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BillResponse
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-04-02T17:49:59.877+05:30[Asia/Kolkata]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillResponse {

	@JsonProperty("ResponseInfo")
	@Valid
	private ResponseInfo responseInfo;

	@JsonProperty("bills")
	@Valid
	private List<Bill> bills;

	@JsonProperty("pagination")
	@Valid
	private Pagination pagination;

	@JsonProperty("statusCount")
	private Map<String, Integer> statusCount;

	public BillResponse addBillItem(Bill billItem) {
		if (this.bills == null) {
			this.bills = new ArrayList<>();
		}
		this.bills.add(billItem);
		return this;
	}

}
