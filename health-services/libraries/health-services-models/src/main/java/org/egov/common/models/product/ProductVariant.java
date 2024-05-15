package org.egov.common.models.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * ProductVariant
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T16:45:24.641+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductVariant {
    @JsonProperty("id")


    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min=2,max=1000)
    private String tenantId = null;

    @JsonProperty("productId")
    @NotNull


    @Size(min = 2, max = 64)

    private String productId = null;

    @JsonProperty("sku")


    @Size(min = 0, max = 1000)

    private String sku = null;

    @JsonProperty("variation")
    @NotNull


    @Size(min = 0, max = 1000)

    private String variation = null;

    @JsonProperty("additionalFields")

    @Valid


    private AdditionalFields additionalFields = null;

    @JsonProperty("isDeleted")


    private Boolean isDeleted = null;

    @JsonProperty("rowVersion")


    private Integer rowVersion = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

}

