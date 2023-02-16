package org.egov.egovsurveyservices.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.egovsurveyservices.helper.SurveyRequestTestBuilder;
import org.egov.egovsurveyservices.producer.Producer;
import org.egov.egovsurveyservices.repository.SurveyRepository;
import org.egov.egovsurveyservices.utils.SurveyUtil;
import org.egov.egovsurveyservices.validators.SurveyValidator;
import org.egov.egovsurveyservices.web.models.SurveyRequest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SurveyServiceCreateTest {

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

    private void mockGetIdList() {
        when(surveyUtil.getIdList(
                any(RequestInfo.class),
                any(String.class),
                any(String.class),
                any(String.class),
                any(Integer.class)
        )).thenReturn(
                Collections.singletonList(
                       "id"
                )
        );
    }

    @Test
    @DisplayName("should call producer with list to save")
    void shouldCallProducerWithListToSave() throws Exception {
        SurveyRequest surveyRequest = SurveyRequestTestBuilder.builder().withSurveyRequest().withRequestInfo().build();
        mockGetIdList();

        surveyService.createSurvey(surveyRequest);

        verify(producer, times(1)).push(anyString(), anyList());
    }

}
