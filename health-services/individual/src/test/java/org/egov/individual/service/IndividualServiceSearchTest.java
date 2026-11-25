package org.egov.individual.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.individual.Individual;
import org.egov.common.service.IdGenService;
import org.egov.individual.helper.IndividualSearchTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.IndividualRepository;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndividualServiceSearchTest {

    @InjectMocks
    private IndividualService individualService;


    @Mock
    private IdGenService idGenService;

    @Mock
    private IndividualRepository individualRepository;

    @Mock
    private IndividualEncryptionService encryptionService;

    @Mock
    private org.egov.individual.config.IndividualProperties properties;

    @Mock
    private EnrichmentService enrichmentService;

    @Mock
    private UserIntegrationService userIntegrationService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private org.egov.individual.util.BeneficiaryIdGenUtil beneficiaryIdGenUtil;


    @Test
    @DisplayName("should search only by id if only id is present")
    void shouldSearchOnlyByIdIfOnlyIdIsPresent() throws QueryBuilderException, InvalidTenantIdException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byId()
                .build();
        RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();

        when(individualRepository.findById(anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(SearchResponse.<Individual>builder().totalCount(1L).response(Collections.singletonList(IndividualTestBuilder.builder()
                        .withId("some-id")
                        .build())).build());

        individualService.search(individualSearch, 0, 10,
                "default", null, false,requestInfo);

        verify(individualRepository, times(1)).findById(anyString(), anyList(),
                eq("id"), anyBoolean());
    }

    @Test
    @DisplayName("should not throw exception in case the array is null")
    void shouldNotThrowExceptionIfArrayIsNull() throws QueryBuilderException, InvalidTenantIdException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byNullId()
                .build();
        RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();

        when(individualRepository.find(any(IndividualSearch.class), anyInt(), anyInt(), anyString(),
                any(), anyBoolean())).thenReturn(SearchResponse.<Individual>builder()
                .totalCount(1L)
                .response(Collections.singletonList(IndividualTestBuilder.builder()
                .withId("some-id")
                .build())).build());

        individualService.search(individualSearch, 0, 10,
                "default", null, false, requestInfo);

        verify(individualRepository, times(0)).findById(anyString() , anyList(),
                eq("id"), anyBoolean());
    }

    @Test
    @DisplayName("should search only clientReferenceId if only clientReferenceId is present")
    void shouldSearchByOnlyClientReferenceIdIfOnlyClientReferenceIdIsPresent() throws QueryBuilderException, InvalidTenantIdException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byClientReferenceId()
                .build();
        RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();

        when(individualRepository.findById(anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(SearchResponse.<Individual>builder().response(Collections.singletonList(IndividualTestBuilder.builder()
                        .withId("some-id")
                        .build())).build());

        individualService.search(individualSearch, 0, 10,
                "default", null, false,requestInfo);

        verify(individualRepository, times(1)).findById(anyString(), anyList(),
                eq("clientReferenceId"), anyBoolean());
    }

    @Test
    @DisplayName("should not call findById if parameters other than id are present")
    void shouldNotCallFindByIdIfParametersOtherThanIdArePresent() throws QueryBuilderException, InvalidTenantIdException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byClientReferenceId()
                .byName()
                .build();

        RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();

        when(individualRepository.find(any(IndividualSearch.class), anyInt(), anyInt(), anyString(), any(), anyBoolean()))
                .thenReturn(SearchResponse.<Individual>builder().build());

        individualService.search(individualSearch, 0, 10,
                "default", null, false,requestInfo);

        verify(individualRepository, times(0)).findById(anyString(), anyList(),
                eq("clientReferenceId"), anyBoolean());
    }

    @Test
    @DisplayName("should call find if parameters other than id are present")
    void shouldCallFindIfParametersOtherThanIdArePresent() throws QueryBuilderException, InvalidTenantIdException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byClientReferenceId()
                .byName()
                .build();
        RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();

        when(individualRepository.find(any(IndividualSearch.class), anyInt(), anyInt(), anyString(),
                any(), anyBoolean())).thenReturn(SearchResponse.<Individual>builder().totalCount(1L).response(Collections.singletonList(IndividualTestBuilder.builder()
                .withId("some-id")
                .build())).build());

        individualService.search(individualSearch, 0, 10,
                "default", null, false,requestInfo);

        verify(individualRepository, times(1))
                .find(individualSearch, 0, 10, "default", null, false);
    }

    @Test
    @DisplayName("search by a field that is not offered: the caller gets INDIVIDUAL_SEARCH_FIELD_NOT_SUPPORTED, not DATABASE_ERROR")
    void unsupportedSearchFieldKeepsItsCode() throws QueryBuilderException, InvalidTenantIdException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder().byGender().build();
        when(individualRepository.find(any(IndividualSearch.class), any(), any(), anyString(), any(), any()))
                .thenThrow(new CustomException("INDIVIDUAL_SEARCH_FIELD_NOT_SUPPORTED", "not supported"));

        CustomException e = assertThrows(CustomException.class, () -> individualService.search(individualSearch, 0, 10,
                "default", null, false, RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()));

        assertEquals("INDIVIDUAL_SEARCH_FIELD_NOT_SUPPORTED", e.getCode());
    }

    @Test
    @DisplayName("other search errors still answer DATABASE_ERROR, as before")
    void otherSearchErrorsStillDatabaseError() throws QueryBuilderException, InvalidTenantIdException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder().byGender().build();
        when(individualRepository.find(any(IndividualSearch.class), any(), any(), anyString(), any(), any()))
                .thenThrow(new CustomException("INVALID_SEARCH_PARAM", "Cannot specify createdToDate without a createdFromDate"));

        CustomException e = assertThrows(CustomException.class, () -> individualService.search(individualSearch, 0, 10,
                "default", null, false, RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()));

        assertEquals("DATABASE_ERROR", e.getCode());
    }
}
