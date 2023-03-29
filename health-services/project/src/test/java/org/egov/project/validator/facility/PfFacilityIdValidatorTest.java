package org.egov.project.validator.facility;


import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkResponse;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.ProjectFacilityBulkRequestTestBuilder;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PfFacilityIdValidatorTest {

    @InjectMocks
    private PfFacilityIdValidator pfFacilityIdValidator;

    @Mock
    private ServiceRequestClient serviceRequestClient;

    @Mock
    private ProjectConfiguration projectConfiguration;

    @BeforeEach
    void setUp() {
        when(projectConfiguration.getFacilityServiceHost())
                .thenReturn("facility-host");
        when(projectConfiguration.getFacilityServiceSearchUrl())
                .thenReturn("facility-search");
        when(projectConfiguration.getSearchApiLimit())
                .thenReturn("1000");
    }

    @Test
    @DisplayName("should add project facility to error details if is Deleted is true")
    void shouldAddProjectFacilityToErrorDetailsIfIsDeletedIsTrue() throws Exception {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();

        when(serviceRequestClient.fetchResult(any(StringBuilder.class), any(), eq(FacilityBulkResponse.class)))
                .thenReturn(FacilityBulkResponse.builder().facilities(Collections.emptyList()).build());

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfFacilityIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add project facility to error details if is Deleted is false")
    void shouldNotAddProjectFacilityToErrorDetailsIfIsDeletedIsFalse() throws Exception {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();

        List<String> projectIdList = new ArrayList<>();
        projectIdList.add("some-project-id");

        when(serviceRequestClient.fetchResult(any(StringBuilder.class), any(), eq(FacilityBulkResponse.class)))
                .thenReturn(FacilityBulkResponse.builder()
                        .facilities(Collections.singletonList(Facility.builder()
                                        .id("facility-id")
                                .build())).build());

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfFacilityIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }
}
