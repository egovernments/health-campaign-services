package org.egov.egovsurveyservices.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.egovsurveyservices.web.models.AnswerEntity;
import org.egov.egovsurveyservices.web.models.AnswerRequest;
import org.egov.egovsurveyservices.web.models.AuditDetails;
import org.egov.egovsurveyservices.web.models.Question;
import org.egov.egovsurveyservices.web.models.SurveyEntity;
import org.egov.egovsurveyservices.web.models.SurveyRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static org.egov.egovsurveyservices.utils.SurveyServiceConstants.ACTIVE;


@Service
@Slf4j
public class EnrichmentService {

    public void enrichSurveyEntity(SurveyRequest surveyRequest) {
        SurveyEntity surveyEntity = surveyRequest.getSurveyEntity();
        surveyEntity.setStatus(ACTIVE);
        surveyEntity.setActive(Boolean.TRUE);
        surveyEntity.setAuditDetails(AuditDetails.builder()
                .createdBy(surveyRequest.getRequestInfo().getUserInfo().getUuid())
                .lastModifiedBy(surveyRequest.getRequestInfo().getUserInfo().getUuid())
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .build());
        surveyEntity.setPostedBy(surveyRequest.getRequestInfo().getUserInfo().getName());

        for(int i = 0; i < surveyEntity.getQuestions().size(); i++) {
            Question question = surveyEntity.getQuestions().get(i);
            question.setQorder((long)i+1);
            question.setUuid(UUID.randomUUID().toString());
            question.setSurveyId(surveyEntity.getUuid());
            question.setAuditDetails(AuditDetails.builder()
                    .createdBy(surveyRequest.getRequestInfo().getUserInfo().getUuid())
                    .lastModifiedBy(surveyRequest.getRequestInfo().getUserInfo().getUuid())
                    .createdTime(System.currentTimeMillis())
                    .lastModifiedTime(System.currentTimeMillis())
                    .build());
        }
    }

    public void enrichAnswerEntity(AnswerRequest answerRequest, Boolean collectCitizenInfo) {
        RequestInfo requestInfo = answerRequest.getRequestInfo();
        AnswerEntity answerEntity = answerRequest.getAnswerEntity();
        answerEntity.getAnswers().forEach(answer -> {
            answer.setUuid(UUID.randomUUID().toString());
            answer.setCitizenId(requestInfo.getUserInfo().getUuid());
            answer.setAuditDetails(AuditDetails.builder()
                    .createdBy(requestInfo.getUserInfo().getUuid())
                    .lastModifiedBy(requestInfo.getUserInfo().getUuid())
                    .createdTime(System.currentTimeMillis())
                    .lastModifiedTime(System.currentTimeMillis())
                    .build());
            if(collectCitizenInfo){
                answer.setEmailId(requestInfo.getUserInfo().getEmailId());
                answer.setMobileNumber(requestInfo.getUserInfo().getMobileNumber());
            }
        });
    }
}
