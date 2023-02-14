package org.egov.egovsurveyservices.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class SurveyResultsSearchCriteria {

    @JsonProperty("surveyId")
    private String surveyId;

    @JsonProperty("citizensUuids")
    private List<String> citizenUuids;

    @JsonProperty("offset")
    private Long offset;

    @JsonProperty("limit")
    private Long limit;

}
