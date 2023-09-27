package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectStaffIndexV1 {
    @JsonProperty("id")
    private String id;
    @JsonProperty("userId")
    private String userId;
    @JsonProperty("projectId")
    private String projectId;
    @JsonProperty("role")
    private String role;
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
    @JsonProperty("createdBy")
    private String createdBy;
    @JsonProperty("lastModifiedBy")
    private String lastModifiedBy;
    @JsonProperty("createdTime")
    private Long createdTime;
    @JsonProperty("lastModifiedTime")
    private Long lastModifiedTime;

}
