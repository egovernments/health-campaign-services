package org.egov.egovsurveyservices.web.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class SurveySearchCriteria {

    @JsonProperty("tenantIds")
    private List<String> tenantIds;

    @JsonProperty("title")
    private String title;

    @JsonProperty("postedBy")
    private String postedBy;

    @JsonProperty("status")
    private String status;

    @JsonProperty("offset")
    private Integer offset;

    @JsonProperty("limit")
    private Integer limit;

    @JsonIgnore
    private String uuid;

    @JsonIgnore
    private List<String> listOfSurveyIds;

    @JsonIgnore
    private Boolean isCountCall = false;

    @JsonIgnore
    private Boolean includeDeleted = false;

    @JsonIgnore
    private List<String> tag;

    @JsonIgnore
    private String entityType;

    @JsonIgnore
    private String citizenId = null;

}
