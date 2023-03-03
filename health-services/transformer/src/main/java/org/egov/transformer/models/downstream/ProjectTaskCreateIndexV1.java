package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectTaskCreateIndexV1 {
    @JsonProperty("id")
    private String id;
    @JsonProperty("taskType")
    private String taskType;
    @JsonProperty("userId")
    private String userId;
    @JsonProperty("projectId")
    private String projectId;
    @JsonProperty("startDate")
    private Long startDate;
    @JsonProperty("endDate")
    private Long endDate;
    @JsonProperty("productVariant")
    private String productVariant;
    @JsonProperty("quantity")
    private Long quantity;
    @JsonProperty("deliveredTo")
    private String deliveredTo;
    @JsonProperty("isDelivered")
    private boolean isDelivered;
    @JsonProperty("deliveryComments")
    private String deliveryComments;
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
    @JsonProperty("createdTime")
    private Long createdTime;
}
