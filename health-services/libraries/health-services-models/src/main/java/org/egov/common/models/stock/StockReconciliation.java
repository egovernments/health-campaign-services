package org.egov.common.models.stock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.egov.tracer.model.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * StockReconciliation
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReconciliation extends EgovOfflineModel {

    @JsonProperty("facilityId")
    @NotNull
    @Size(min=2, max=64)
    private String facilityId = null;

    @JsonProperty("productVariantId")
    @NotNull
    @Size(min=2, max=64)
    private String productVariantId = null;

    @JsonProperty("referenceId")
    private String referenceId = null;

    @JsonProperty("referenceIdType")
    @Size(min=2, max=64)
    private String referenceIdType = null;

    @JsonProperty("physicalCount")
    private Integer physicalCount = null;

    @JsonProperty("calculatedCount")
    private Integer calculatedCount = null;

    @JsonProperty("commentsOnReconciliation")
    private String commentsOnReconciliation = null;

    @JsonProperty("dateOfReconciliation")
    private Long dateOfReconciliation = null;

    //TODO remove this
    @JsonProperty("isDeleted")
    private Boolean isDeleted = null;

}

