package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.stock.AdditionalFields;
import org.egov.common.models.stock.TransactionReason;
import org.egov.common.models.stock.TransactionType;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockIndexV1 {

    @JsonProperty("id")
    private String id;

    @JsonProperty("facilityId")
    private String facilityId;

    @JsonProperty("transactingFacilityId")
    private String transactingFacilityId;

    @JsonProperty("facilityName")
    private String facilityName;

    @JsonProperty("transactingPartyName")
    private String transactingPartyName;

    @JsonProperty("transactingPartyType")
    private String transactingPartyType;

    @JsonProperty("productVariant")
    private String productVariant;

    @JsonProperty("physicalCount")
    private Integer physicalCount;

    @JsonProperty("eventType")
    private TransactionType eventType;

    @JsonProperty("reason")
    private TransactionReason reason;

    @JsonProperty("eventTimeStamp")
    private Long eventTimeStamp;

    @JsonProperty("dateOfEntry")
    private Long dateOfEntry;

    @JsonProperty("province")
    private String province;

    @JsonProperty("district")
    private String district;

    @JsonProperty("administrativeProvince")
    private String administrativeProvince;

    @JsonProperty("locality")
    private String locality;

    @JsonProperty("village")
    private String village;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("createdBy")
    private String createdBy;

    @JsonProperty("lastModifiedBy")
    private String lastModifiedBy;

    @JsonProperty("createdTime")
    private Long createdTime;

    @JsonProperty("lastModifiedTime")
    private Long lastModifiedTime;

    @JsonProperty("additionalFields")
    private AdditionalFields additionalFields;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("facilityType")
    private String facilityType;

    @JsonProperty("transactingFacilityType")
    private String transactingFacilityType;
}
