package org.egov.transformer.models.downstream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectTaskCreateIndexV1 {
    private String id;
    private String taskType;
    private String userId;
    private String projectId;
    private Long startDate;
    private Long endDate;
    private String productVariantId;
    private Long quantity;
    private String deliveredTo;
    private boolean isDelivered;
    private String deliveryComments;
    private String province;
    private String district;
    private String administrativeProvince;
    private String locality;
    private String village;
    private Double latitude;
    private Double longitude;
    private Long createdTime;
}
