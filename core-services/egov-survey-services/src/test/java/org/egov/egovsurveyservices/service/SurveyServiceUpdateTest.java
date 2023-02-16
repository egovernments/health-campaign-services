package org.egov.egovsurveyservices.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.egovsurveyservices.helper.SurveyRequestTestBuilder;
import org.egov.egovsurveyservices.producer.Producer;
import org.egov.egovsurveyservices.repository.SurveyRepository;
import org.egov.egovsurveyservices.utils.SurveyUtil;
import org.egov.egovsurveyservices.validators.SurveyValidator;
import org.egov.egovsurveyservices.web.models.SurveyEntity;
import org.egov.egovsurveyservices.web.models.SurveyRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SurveyServiceUpdateTest {

    @InjectMocks
    SurveyService surveyService;

    @Mock
    private SurveyValidator surveyValidator;

    @Mock
    private Producer producer;

    @Mock
    private EnrichmentService enrichmentService;

    @Mock
    private SurveyRepository surveyRepository;

    @Mock
    private SurveyUtil surveyUtil;

    @Test
    @DisplayName("should call producer with list to update")
    void shouldCallProducerWithListToUpdate() {
        SurveyRequest surveyRequest = SurveyRequestTestBuilder.builder().withSurveyRequest().withRequestInfo().build();
        mockSurveyValidator(surveyRequest);

        surveyService.updateSurvey(surveyRequest);

        verify(producer, times(1)).push(anyString(), anyList());
    }

    private void mockSurveyValidator(SurveyRequest surveyRequest) {
        when(surveyValidator.validateSurveyExistence(
                any(SurveyEntity.class)
        )).thenReturn(
                surveyRequest.getSurveyEntity()
        );
    }

}
