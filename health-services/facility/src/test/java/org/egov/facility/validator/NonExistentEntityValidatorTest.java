package org.egov.facility.validator;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.models.household.Household;
import org.egov.facility.helper.FacilityBulkRequestTestBuilder;
import org.egov.facility.helper.FacilityTestBuilder;
import org.egov.facility.repository.FacilityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NonExistentEntityValidatorTest {

    @InjectMocks
    private FNonExistentValidator fNonExistentValidator;
    
    @Mock
    private FacilityRepository facilityRepository;
    
    @Test
    @DisplayName("should add to error details map if entity not found")
    void shouldAddToErrorDetailsMapIfEntityNotFound() throws InvalidTenantIdException {
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder().withFacilityId("some-id").withRequestInfo().build();
        when(facilityRepository.findById(anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(SearchResponse.<Facility>builder().build());

        Map<Facility, List<Error>> errorDetailsMap = fNonExistentValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error details map if entity found")
    void shouldNotAddToErrorDetailsMapIfEntityFound() throws InvalidTenantIdException {
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder().withFacilityId("some-id").withRequestInfo().build();
        when(facilityRepository.findById(anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(SearchResponse.<Facility>builder().
                        response(Collections.singletonList(FacilityTestBuilder.builder().withFacility().withId("some-id").build()))
                        .build());

        Map<Facility, List<Error>> errorDetailsMap = fNonExistentValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }
}
