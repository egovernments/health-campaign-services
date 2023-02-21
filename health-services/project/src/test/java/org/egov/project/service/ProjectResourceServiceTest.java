package org.egov.project.service;

import org.egov.project.helper.ProjectResourceRequestTestBuilder;
import org.egov.project.web.models.ProjectResource;
import org.egov.project.web.models.ProjectResourceRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class ProjectResourceServiceTest {

    @InjectMocks
    private ProjectResourceService service;

    @Test
    void shouldReturnProjectResourceResponse(){
        ProjectResourceRequest request = ProjectResourceRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();

        ProjectResource response = service.create(request);

        assertNotNull(response);
    }
}
