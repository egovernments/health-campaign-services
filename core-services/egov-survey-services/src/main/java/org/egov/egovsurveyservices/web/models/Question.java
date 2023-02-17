package org.egov.egovsurveyservices.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.egov.egovsurveyservices.web.models.enums.Type;

import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Question {

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("surveyId")
    private String surveyId;

    @NotNull
    @JsonProperty("questionStatement")
    private String questionStatement;

    @JsonProperty("options")
    private List<String> options;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    @JsonProperty("status")
    private String status;

    @JsonProperty("type")
    @NotNull(message="The value provided is either Invalid or null")
    private Type type;

    @JsonProperty("required")
    private Boolean required;

    @JsonProperty("qorder")
    private Long qorder;

    @JsonProperty("extraInfo")
    private String extraInfo;

}
