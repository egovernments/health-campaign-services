package org.egov.project.validator;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.Error;
import org.egov.project.helper.ProjectResourceTestBuilder;
import org.egov.project.repository.ProjectResourceRepository;
import org.egov.project.validator.resource.PrUniqueCombinationValidator;
import org.egov.project.web.models.ProjectResource;
import org.egov.project.web.models.ProjectResourceBulkRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrUniqueCombinationValidatorTest {

    @InjectMocks
    private PrUniqueCombinationValidator validator;

    @Mock
    private ProjectResourceRepository projectResourceRepository;

    @BeforeEach
    void setUp() {
        when(projectResourceRepository.findById(any(List.class), anyBoolean(), anyString()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("should add to error if duplicate mapping is found")
    void shouldAddErrorDetailsIfDuplicateFound() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequest.builder().projectResource(Arrays.asList(
                        ProjectResourceTestBuilder.builder().withProjectId("ID101").withProductVariantId("PV101").build(),
                        ProjectResourceTestBuilder.builder().withProjectId("ID101").withProductVariantId("PV101").build()
                )).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        Map<ProjectResource, List<Error>> errorDetailsMap = validator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error if duplicate mapping is not found")
    void shouldNotAddErrorDetailsIfDuplicateNotFound() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequest.builder().projectResource(Arrays.asList(
                ProjectResourceTestBuilder.builder().withProjectId("ID101").withProductVariantId("PV101").build(),
                ProjectResourceTestBuilder.builder().withProjectId("ID101").withProductVariantId("PV102").build()
        )).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        Map<ProjectResource, List<Error>> errorDetailsMap = validator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }
}
