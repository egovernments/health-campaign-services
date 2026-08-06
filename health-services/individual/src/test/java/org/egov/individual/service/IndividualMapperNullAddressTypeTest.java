package org.egov.individual.service;

import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.AddressType;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.Name;
import org.egov.common.models.individual.UserDetails;
import org.egov.common.contract.user.enums.UserType;
import org.egov.individual.config.IndividualProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;

/**
 * Part 1 guard: address.type is nullable now, so the CORRESPONDENCE filter must not dereference it.
 */
@ExtendWith(MockitoExtension.class)
class IndividualMapperNullAddressTypeTest {

    @Mock
    private IndividualProperties properties;

    private Individual individualWith(Address... addresses) {
        return Individual.builder()
                .tenantId("default")
                .name(Name.builder().givenName("gn").familyName("fn").build())
                .userDetails(UserDetails.builder()
                        .username("uname")
                        .password("pwd")
                        .userType(UserType.CITIZEN)
                        .roles(Collections.emptyList())
                        .build())
                .address(Arrays.asList(addresses))
                .build();
    }

    private void stubProperties() {
        lenient().when(properties.getUserServiceUserType()).thenReturn("CITIZEN");
        lenient().when(properties.isUserServiceAccountLocked()).thenReturn(false);
    }

    @Test
    @DisplayName("a single null typed address must not NPE and yields no correspondence address")
    void nullAddressTypeDoesNotNpe() {
        stubProperties();
        Individual individual = individualWith(Address.builder()
                .tenantId("default").type(null).addressLine1("line-1").build());

        assertNull(assertDoesNotThrow(() ->
                IndividualMapper.toUserRequest(individual, properties)).getCorrespondenceAddress());
    }

    @Test
    @DisplayName("a null typed address before a correspondence address must not hide it")
    void nullTypedAddressDoesNotShadowCorrespondence() {
        stubProperties();
        Individual individual = individualWith(
                Address.builder().tenantId("default").type(null).addressLine1("untyped").build(),
                Address.builder().tenantId("default").type(AddressType.CORRESPONDENCE)
                        .addressLine1("correspondence").build());

        assertEquals("correspondence", assertDoesNotThrow(() ->
                IndividualMapper.toUserRequest(individual, properties)).getCorrespondenceAddress());
    }
}
