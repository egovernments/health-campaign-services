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
public class Answer {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("questionId")
    private String questionId;

    @JsonProperty("answer")
    private List<Object> answer;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    // we are also allowing employee ids in this field
    @JsonProperty("citizenId")
    private String citizenId;

    @JsonProperty("mobileNumber")
    private String mobileNumber;

    @JsonProperty("emailId")
    private String emailId;

    @JsonProperty("additionalComments")
    private String additionalComments;

    @JsonProperty("entityId")
    private String entityId;

}
