package org.egov.facility.validator;

import org.egov.common.models.Error;
import org.egov.facility.helper.FacilityBulkRequestTestBuilder;
import org.egov.facility.web.models.Facility;
import org.egov.facility.web.models.FacilityBulkRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class UniqueEntityValidatorTest {

    @InjectMocks
    private FUniqueEntityValidator fUniqueEntityValidator;

    @Test
    @DisplayName("should add to error if duplicate entity is found")
    void shouldAddErrorDetailsIfDuplicateFound() {
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder().withFacility().withFacility().withRequestInfo().build();
        request.getFacilities().get(0).setId("some-id");
        request.getFacilities().get(1).setId("some-id");

        Map<Facility, List<Error>> errorDetailsMap = fUniqueEntityValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error if duplicate entity is not found")
    void shouldNotAddErrorDetailsIfDuplicateNotFound() {
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder().withFacility().withFacility().withRequestInfo().build();
        request.getFacilities().get(0).setId("some-id");
        request.getFacilities().get(1).setId("some-other-id");

        Map<Facility, List<Error>> errorDetailsMap = fUniqueEntityValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }
}
