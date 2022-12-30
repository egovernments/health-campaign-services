package org.egov.individual.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.service.IdGenService;
import org.egov.individual.helper.IndividualRequestTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.ApiOperation;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndividualServiceTest {

    @InjectMocks
    private IndividualService individualService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private IndividualRepository individualRepository;

    @BeforeEach
    void setUp() throws Exception {
        mockIdGen("individual.id", "some-individual-id");
        mockIdGen("sys.gen.identifier.id", "some-sys-gen-id");
    }

    private void mockIdGen(String value, String o) throws Exception {
        when(idGenService.getIdList(any(RequestInfo.class), anyString(),
                eq(value), eq(null), anyInt()))
                .thenReturn(Collections.singletonList(o));
    }

    @Test
    @DisplayName("should generate address id if address is not null")
    void shouldGenerateAddressIdIfAddressIsNotNull() throws Exception {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.CREATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(IndividualTestBuilder.builder()
                        .withName()
                        .withTenantId()
                        .withAddress()
                        .build())
                .build();

        List<Individual> response = individualService.create(request);

        assertNotNull(response.stream().findFirst().get()
                        .getAddress().stream().findFirst().get()
                        .getId());
    }

    @Test
    @DisplayName("should enrich address audit details and individual id if address is not null")
    void shouldEnrichAddressWithAuditDetailsAndIndividualIdIfAddressIsNotNull() throws Exception {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.CREATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(IndividualTestBuilder.builder()
                        .withName()
                        .withTenantId()
                        .withAddress()
                        .build())
                .build();

        List<Individual> response = individualService.create(request);

        assertNotNull(response.stream().findFirst().get()
                .getAddress().stream().findFirst().get()
                .getId());
        assertNotNull(response.stream().findFirst().get()
                .getAddress().stream().findFirst().get()
                .getIndividualId());
        assertNotNull(response.stream().findFirst().get()
                .getAddress().stream().findFirst().get()
                .getAuditDetails());
    }

    @Test
    @DisplayName("should generate individual id")
    void shouldGenerateIndividualId() throws Exception {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.CREATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(IndividualTestBuilder.builder()
                        .withTenantId()
                        .withName()
                        .build())
                .build();

        List<Individual> response = individualService.create(request);

        assertEquals("some-individual-id",
                response.stream().findFirst().get()
                        .getId());
    }

    @Test
    @DisplayName("should enrich individuals")
    void shouldEnrichIndividuals() throws Exception {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.CREATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(IndividualTestBuilder.builder()
                        .withTenantId()
                        .withName()
                        .build())
                .build();

        List<Individual> response = individualService.create(request);

        assertEquals("some-individual-id",
                response.stream().findFirst().get()
                        .getId());
        assertEquals(1, response.stream().findFirst().get().getRowVersion());
        assertFalse(response.stream().findFirst().get().getIsDeleted());
        assertNotNull(response.stream().findFirst().get().getAuditDetails());
    }

    @Test
    @DisplayName("should generate identifier if not present")
    void shouldGenerateIdentifierIfNotPresent() throws Exception {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.CREATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(IndividualTestBuilder.builder()
                        .withTenantId()
                        .withName()
                        .build())
                .build();

        List<Individual> response = individualService.create(request);

        assertNotNull(response.stream().findFirst().get()
                .getIdentifiers().stream().findFirst().get()
                .getIdentifierId());
        assertEquals("SYSTEM_GENERATED",
                response.stream().findFirst().get()
                        .getIdentifiers().stream().findFirst().get()
                        .getIdentifierType());
    }

    @Test
    @DisplayName("should enrich identifier with individual id")
    void shouldEnrichIdentifierWithIndividualId() throws Exception {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.CREATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(IndividualTestBuilder.builder()
                        .withTenantId()
                        .withName()
                        .build())
                .build();

        List<Individual> response = individualService.create(request);

        assertNotNull(response.stream().findFirst().get()
                .getIdentifiers().stream().findFirst().get()
                .getIdentifierId());
        assertEquals("SYSTEM_GENERATED",
                response.stream().findFirst().get()
                        .getIdentifiers().stream().findFirst().get()
                        .getIdentifierType());
        assertEquals("some-individual-id",
                response.stream().findFirst().get()
                        .getIdentifiers().stream().findFirst().get()
                        .getIndividualId());
    }

    @Test
    @DisplayName("should enrich identifiers")
    void shouldEnrichIdentifiers() throws Exception {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.CREATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(IndividualTestBuilder.builder()
                        .withTenantId()
                        .withName()
                        .build())
                .build();

        List<Individual> response = individualService.create(request);

        assertNotNull(response.stream().findFirst().get()
                .getIdentifiers().stream().findFirst().get()
                .getIdentifierId());
        assertEquals("SYSTEM_GENERATED",
                response.stream().findFirst().get()
                        .getIdentifiers().stream().findFirst().get()
                        .getIdentifierType());
        assertNotNull(
                response.stream().findFirst().get()
                        .getIdentifiers().stream().findFirst().get()
                        .getId());
        assertEquals("some-sys-gen-id",
                response.stream().findFirst().get()
                        .getIdentifiers().stream().findFirst().get()
                        .getIdentifierId());
        assertNotNull(response.stream().findFirst().get()
                .getIdentifiers().stream().findFirst().get()
                .getAuditDetails());
    }

    @Test
    @DisplayName("should save individuals")
    void shouldSaveIndividuals() throws Exception {
        IndividualRequest request = IndividualRequestTestBuilder.builder()
                .withApiOperation(ApiOperation.CREATE)
                .withRequestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .withIndividuals(IndividualTestBuilder.builder()
                        .withTenantId()
                        .withName()
                        .build())
                .build();

        individualService.create(request);

        verify(individualRepository, times(1))
                .save(anyList(), anyString());
    }
}