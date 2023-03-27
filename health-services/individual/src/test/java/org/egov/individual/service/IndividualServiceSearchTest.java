package org.egov.individual.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.service.IdGenService;
import org.egov.individual.helper.IndividualSearchTestBuilder;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.util.EncryptionDecryptionUtil;
import org.egov.individual.web.models.IndividualSearch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndividualServiceSearchTest {

    @InjectMocks
    private IndividualService individualService;


    @Mock
    private IdGenService idGenService;

    @Mock
    private IndividualRepository individualRepository;

    @Mock
    private EncryptionDecryptionUtil encryptionDecryptionUtil;

    @Test
    @DisplayName("should search only by id if only id is present")
    void shouldSearchOnlyByIdIfOnlyIdIsPresent() throws QueryBuilderException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byId()
                .build();
        RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();

        individualService.search(individualSearch, 0, 10,
                "default", null, false,requestInfo);

        verify(individualRepository, times(1)).findById(anyList(),
                eq("id"), anyBoolean());
    }

    @Test
    @DisplayName("should not throw exception in case the array is null")
    void shouldNotThrowExceptionIfArrayIsNull() throws QueryBuilderException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byNullId()
                .build();
        RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();
        when(encryptionDecryptionUtil.encryptObject(any(Object.class), any(String.class), any(Class.class))).thenReturn(individualSearch);
        individualService.search(individualSearch, 0, 10,
                "default", null, false,requestInfo);

        verify(individualRepository, times(0)).findById(anyList(),
                eq("id"), anyBoolean());
    }

    @Test
    @DisplayName("should search only clientReferenceId if only clientReferenceId is present")
    void shouldSearchByOnlyClientReferenceIdIfOnlyClientReferenceIdIsPresent() throws QueryBuilderException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byClientReferenceId()
                .build();
        RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();

        individualService.search(individualSearch, 0, 10,
                "default", null, false,requestInfo);

        verify(individualRepository, times(1)).findById(anyList(),
                eq("clientReferenceId"), anyBoolean());
    }

    @Test
    @DisplayName("should not call findById if parameters other than id are present")
    void shouldNotCallFindByIdIfParametersOtherThanIdArePresent() throws QueryBuilderException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byClientReferenceId()
                .byName()
                .build();

        RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();
        when(encryptionDecryptionUtil.encryptObject(any(Object.class), any(String.class), any(Class.class))).thenReturn(individualSearch);
        individualService.search(individualSearch, 0, 10,
                "default", null, false,requestInfo);

        verify(individualRepository, times(0)).findById(anyList(),
                eq("clientReferenceId"), anyBoolean());
    }

    @Test
    @DisplayName("should call find if parameters other than id are present")
    void shouldCallFindIfParametersOtherThanIdArePresent() throws QueryBuilderException {
        IndividualSearch individualSearch = IndividualSearchTestBuilder.builder()
                .byClientReferenceId()
                .byGender()
                .build();
        RequestInfo requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();
        when(encryptionDecryptionUtil.encryptObject(any(Object.class), any(String.class), any(Class.class))).thenReturn(individualSearch);
        individualService.search(individualSearch, 0, 10,
                "default", null, false,requestInfo);

        verify(individualRepository, times(1))
                .find(individualSearch, 0, 10, "default", null, false);
    }
}
