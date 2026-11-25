package org.egov.individual.service;

import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.StoredIdentifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndividualEncryptionServiceTest {

    @Test
    @DisplayName("duplicate Aadhaar check searches on the hash with no isDeleted filter (soft-deleted identifiers still count)")
    void aadhaarCheckHasNoDeletedFilter() throws Exception {
        IndividualPiiCryptoService crypto = mock(IndividualPiiCryptoService.class);
        IndividualRepository repo = mock(IndividualRepository.class);
        StoredIdentifier stored = new StoredIdentifier();
        stored.setIdentifierType("AADHAAR");
        stored.setIdentifierId("228075|cipher");
        stored.setIdentifierIdHash("hash-1");
        Individual individual = Individual.builder().id("ind-1").tenantId("dev")
                .identifiers(new ArrayList<>(List.<Identifier>of(stored))).build();
        when(crypto.encrypt(any())).thenReturn(List.of(individual));
        when(repo.find(any(), isNull(), isNull(), anyString(), isNull(), anyBoolean()))
                .thenReturn(SearchResponse.<Individual>builder().response(new ArrayList<>()).build());

        new IndividualEncryptionService(crypto, repo).encrypt(new IndividualBulkRequest(), List.of(individual), "key", false);

        ArgumentCaptor<IndividualSearch> search = ArgumentCaptor.forClass(IndividualSearch.class);
        verify(repo).find(search.capture(), isNull(), isNull(), anyString(), isNull(), anyBoolean());
        StoredIdentifier criteria = (StoredIdentifier) search.getValue().getIdentifier();
        assertNull(criteria.getIsDeleted());
        assertEquals("hash-1", criteria.getIdentifierIdHash());
        assertEquals("AADHAAR", criteria.getIdentifierType());
    }
}
