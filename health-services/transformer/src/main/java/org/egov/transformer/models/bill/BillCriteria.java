package org.egov.transformer.models.bill;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.transformer.utils.StringOrListDeserializer;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BillCriteria
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-04-02T17:49:59.877+05:30[Asia/Kolkata]")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillCriteria {
	
	@JsonProperty("tenantId")
	@NotNull
	@Size(min = 2, max = 64)
	private String tenantId;

	@JsonProperty("ids")
	private Set<String> ids;

	@JsonProperty("businessService")
	@Size(min = 2, max = 64)
	private String businessService;

	@JsonProperty("referenceIds")
	private Set<String> referenceIds;

	@JsonProperty("billNumbers")
	private Set<String> billNumbers;

	@JsonProperty("statuses")
	@JsonAlias("status")
	@JsonDeserialize(using = StringOrListDeserializer.class)
	private List<String> statuses;

	@JsonProperty("statusesNot")
	@JsonAlias("statusNot")
	@JsonDeserialize(using = StringOrListDeserializer.class)
	private List<String> statusesNot;

	@JsonProperty("isPaymentStatusNull")
	private Boolean isPaymentStatusNull;

	@JsonProperty("localityCode")
	private String localityCode;

	@JsonProperty("fromDate")
	private Long fromDate;

	@JsonProperty("toDate")
	private Long toDate;

	// V2 Payments - Period-based billing search fields

	@JsonProperty("billingPeriodIds")
	private List<String> billingPeriodIds;

	@JsonProperty("isAggregate")
	private Boolean isAggregate;

	@JsonProperty("billingType")
	private String billingType;

	@JsonProperty("reportStatus")
	private String reportStatus;

	public BillCriteria addIdsItem(String idsItem) {
		if (this.ids == null) {
			this.ids = new HashSet<>();
		}
		this.ids.add(idsItem);
		return this;
	}

	public BillCriteria addReferenceIdItem(String referenceIdItem) {
		if (this.referenceIds == null) {
			this.referenceIds = new HashSet<>();
		}
		this.referenceIds.add(referenceIdItem);
		return this;
	}

	public BillCriteria addBillingPeriodIdItem(String billingPeriodIdItem) {
		if (this.billingPeriodIds == null) {
			this.billingPeriodIds = new ArrayList<>();
		}
		this.billingPeriodIds.add(billingPeriodIdItem);
		return this;
	}

}
