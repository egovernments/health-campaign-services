package org.egov.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class HouseholdRepositoryTest {

    private HouseholdRepository householdRepository;

    @Test
    @DisplayName("should return result if client reference Id already exists")
    void shouldReturnResultIfClientReferenceIdAlreadyExists(){
        List<String> clientRefIds = new ArrayList<>();
        clientRefIds.add("123");
        clientRefIds.add("234");

        List<String> validRefId = householdRepository.validateClientReferenceId(clientRefIds);

        assertEquals(clientRefIds.size(), validRefId.size());
    }
}
