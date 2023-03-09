package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.transformer.models.upstream.TransactionType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StockIndexV1 {

    @JsonProperty("id")
    private String id;

    @JsonProperty("facilityId")
    private String facilityId;

    @JsonProperty("productVariant")
    private String productVariant;

    @JsonProperty("physicalCount")
    private Integer physicalCount;

    @JsonProperty("eventType")
    private TransactionType eventType;

    @JsonProperty("eventTimeStamp")
    private Long eventTimeStamp;

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
}
