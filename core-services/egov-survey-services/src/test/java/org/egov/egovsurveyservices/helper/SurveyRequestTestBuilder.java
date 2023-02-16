package org.egov.egovsurveyservices.helper;


import org.egov.egovsurveyservices.web.models.AdditionalFields;
import org.egov.egovsurveyservices.web.models.Question;
import org.egov.egovsurveyservices.web.models.SurveyEntity;
import org.egov.egovsurveyservices.web.models.SurveyRequest;
import org.egov.egovsurveyservices.web.models.enums.Type;

import java.util.Collections;

public class SurveyRequestTestBuilder {

    private SurveyRequest.SurveyRequestBuilder builder;

    public SurveyRequestTestBuilder() {
        this.builder = SurveyRequest.builder();
    }

    public static SurveyRequestTestBuilder builder() {
        return new SurveyRequestTestBuilder();
    }

    public SurveyRequest build() {
        return this.builder.build();
    }

    public SurveyRequestTestBuilder withSurveyRequest(){
        this.builder.surveyEntity(
                SurveyEntity.builder()
                        .title("test")
                        .tags(Collections.singletonList("tag1"))
                        .tenantIds(Collections.singletonList("default"))
                        .entityType("Warehouse")
                        .auditDetails(
                                AuditDetailsTestBuilder.builder().withAuditDetails().build()
                        )
                        .questions(Collections.singletonList(
                                Question.builder()
                                        .type(Type.CHECKBOX_ANSWER_TYPE)
                                        .questionStatement("question-statement")
                                        .auditDetails(
                                                AuditDetailsTestBuilder.builder().withAuditDetails().build()
                                        )
                                        .options(
                                                Collections.singletonList(
                                                      "Option 1"
                                                )
                                        )
                                        .build()
                        ))
                        .build()
        );
        return this;
    }

    public SurveyRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }

}
