package org.egov.individual.service;

import org.egov.common.models.individual.Individual;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.IndividualRepository;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndividualServiceCacheTest {

    @InjectMocks
    private IndividualService individualService;

    @Mock
    private IndividualRepository individualRepository;

    @Mock
    private IndividualEncryptionService individualEncryptionService;

    @Test
    @DisplayName("bulk API cache gets the stored (encrypted) form, not the plain request")
    void shouldCacheStoredFormOnly() {
        List<Individual> plain = List.of(IndividualTestBuilder.builder().withId().build());
        Individual storedForm = IndividualTestBuilder.builder().withId().build();
        storedForm.setMobileNumber("key-1|c3RvcmVkLWZvcm0tY2lwaGVy"); // must differ by equals (id is not part of it)
        List<Individual> stored = List.of(storedForm);
        when(individualEncryptionService.toStoredForm(plain)).thenReturn(stored);

        individualService.putInCache(plain);

        verify(individualRepository).putInCache(stored);
        verify(individualRepository, never()).putInCache(plain);
    }

    @Test
    @DisplayName("when encryption fails the cache is skipped, plain data is never cached")
    void shouldSkipCacheWhenEncryptionFails() {
        List<Individual> plain = List.of(IndividualTestBuilder.builder().withId().build());
        when(individualEncryptionService.toStoredForm(plain))
                .thenThrow(new CustomException("INDIVIDUAL_ENC_SERVICE_UNAVAILABLE", "down"));

        individualService.putInCache(plain);

        verify(individualRepository, never()).putInCache(any());
    }
}
