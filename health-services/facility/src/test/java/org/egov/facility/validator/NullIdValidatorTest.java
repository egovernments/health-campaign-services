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
class NullIdValidatorTest {

    @InjectMocks
    private FNullIdValidator fNullIdValidator;

    @Test
    @DisplayName("should add to error details if id is null")
    void shouldAddErrorDetailsIfIdNull() {
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();

        Map<Facility, List<Error>> errorDetailsMap = fNullIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add to error details if id is not  null")
    void shouldNotAddErrorDetailsIfIdNotNull() {
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();
        request.getFacilities().get(0).setId("some-id");

        Map<Facility, List<Error>> errorDetailsMap = fNullIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }
}
