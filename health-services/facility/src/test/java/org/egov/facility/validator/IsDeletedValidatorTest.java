package org.egov.facility.validator;


import org.egov.common.models.Error;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.facility.helper.FacilityBulkRequestTestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class IsDeletedValidatorTest {

    @InjectMocks
    private FIsDeletedValidator fIsDeletedValidator;

    @Test
    @DisplayName("should add facility to error details if is Deleted is true")
    void shouldAddFacilityToErrorDetailsIfIsDeletedIsTrue() {
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();
        request.getFacilities().get(0).setIsDeleted(true);

        Map<Facility, List<Error>> errorDetailsMap = fIsDeletedValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add facility to error details if is Deleted is false")
    void shouldAddFacilityToErrorDetailsIfIsDeletedIsFalse() {
        FacilityBulkRequest request = FacilityBulkRequestTestBuilder.builder().withFacility().withRequestInfo().build();

        Map<Facility, List<Error>> errorDetailsMap = fIsDeletedValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }
}
