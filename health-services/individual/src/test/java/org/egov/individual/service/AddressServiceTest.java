package org.egov.individual.service;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.AddressRepository;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.AddressRequest;
import org.egov.individual.web.models.AddressType;
import org.egov.individual.web.models.Individual;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @InjectMocks
    private AddressService addressService;

    @Mock
    private IndividualRepository individualRepository;

    @Mock
    private AddressRepository addressRepository;

    @BeforeEach
    void setUp() {

    }

    @Test
    @DisplayName("should check if individual exists using available id")
    void shouldCheckIfIndividualExistsUsingAvailableId() {
        AddressRequest request = AddressRequest.builder()
                .address(Collections.singletonList(Address.builder()
                                .clientReferenceId("some-address-client-reference-id")
                                .individualClientReferenceId("some-individual-client-reference-id")
                                .doorNo("some-door-no")
                                .type(AddressType.PERMANENT)
                        .build()))
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .build();
        List<Individual> existingIndividuals = Collections.singletonList(IndividualTestBuilder.builder()
                .withClientReferenceId("some-individual-client-reference-id")
                .withTenantId()
                .withName()
                .build());

        when(individualRepository.findById(anyList(), anyString(), anyBoolean()))
                .thenReturn(existingIndividuals);

        assertDoesNotThrow(() -> addressService.create(request));
    }

    @Test
    @DisplayName("should throw exception if individual does not exist for available id")
    void shouldThrowExceptionIfIndividualDoesNotExistForAvailableId() {
        AddressRequest request = AddressRequest.builder()
                .address(Collections.singletonList(Address.builder()
                        .clientReferenceId("some-address-client-reference-id")
                        .individualClientReferenceId("some-individual-client-reference-id")
                        .doorNo("some-door-no")
                        .type(AddressType.PERMANENT)
                        .build()))
                .build();

        when(individualRepository.findById(anyList(), anyString(), anyBoolean()))
                .thenReturn(Collections.emptyList());

        assertThrows(CustomException.class, () -> addressService.create(request));
    }

    @Test
    @DisplayName("should throw exception if address type already exists for an individual")
    void shouldThrowExceptionIfAddressTypeAlreadyExistsForAnIndividual() {
        AddressRequest request = AddressRequest.builder()
                .address(Collections.singletonList(Address.builder()
                        .clientReferenceId("some-address-client-reference-id")
                        .individualId("some-individual-id")
                        .doorNo("some-door-no")
                        .type(AddressType.PERMANENT)
                        .build()))
                .build();
        List<Individual> existingIndividuals = Collections.singletonList(IndividualTestBuilder.builder()
                .withId("some-individual-id")
                .withTenantId()
                .withAddress()
                .withName()
                .build());

        when(individualRepository.findById(anyList(), anyString(), anyBoolean()))
                .thenReturn(existingIndividuals);

        assertThrows(CustomException.class, () -> addressService.create(request));
    }

    @Test
    @DisplayName("should enrich address before create")
    void shouldEnrichAddressBeforeCreate() {
        AddressRequest request = AddressRequest.builder()
                .address(Collections.singletonList(Address.builder()
                        .clientReferenceId("some-address-client-reference-id")
                        .individualId("some-individual-id")
                        .doorNo("some-door-no")
                        .type(AddressType.CORRESPONDENCE)
                        .build()))
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .build();
        List<Individual> existingIndividuals = Collections.singletonList(IndividualTestBuilder.builder()
                .withId("some-individual-id")
                .withTenantId()
                .withAddress()
                .withName()
                .build());

        when(individualRepository.findById(anyList(), anyString(), anyBoolean()))
                .thenReturn(existingIndividuals);

        List<Address> results = addressService.create(request);

        assertNotNull(results.stream().findFirst().get().getId());
        assertNotNull(results.stream().findFirst().get().getAuditDetails());
        assertEquals(1, results.stream().findFirst().get().getRowVersion());
    }

    @Test
    @DisplayName("should save the address")
    void shouldSaveTheAddress() {
        AddressRequest request = AddressRequest.builder()
                .address(Collections.singletonList(Address.builder()
                        .clientReferenceId("some-address-client-reference-id")
                        .individualId("some-individual-id")
                        .doorNo("some-door-no")
                        .type(AddressType.CORRESPONDENCE)
                        .build()))
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .build();
        List<Individual> existingIndividuals = new ArrayList<>();
        existingIndividuals.add(IndividualTestBuilder.builder()
                .withId("some-individual-id")
                .withTenantId()
                .withAddress()
                .withName()
                .build());

        when(individualRepository.findById(anyList(), anyString(), anyBoolean()))
                .thenReturn(existingIndividuals);

        addressService.create(request);

        verify(individualRepository, times(1)).putInCache(anyList());
        verify(addressRepository, times(1)).save(anyList(), anyString());
    }
}