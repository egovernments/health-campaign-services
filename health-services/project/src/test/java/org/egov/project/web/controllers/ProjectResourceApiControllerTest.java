package org.egov.project.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.project.TestConfiguration;
import org.egov.project.helper.ProjectResourceRequestTestBuilder;
import org.egov.project.service.ProjectResourceService;
import org.egov.project.web.models.ProjectResourceRequest;
import org.egov.project.web.models.ProjectResourceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectResourceApiController.class)
@Import(TestConfiguration.class)
public class ProjectResourceApiControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectResourceService projectResourceService;

    @Test
    @DisplayName("should project resource create request pass")
    void shouldProjectResourceCreateRequestPass() throws Exception {
        ProjectResourceRequest projectRequest = ProjectResourceRequestTestBuilder.builder().withProjectResource()
                                                .withRequestInfo().build();
        MvcResult result = mockMvc.perform(post("/resource/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isAccepted()).andReturn();

        ProjectResourceResponse response = objectMapper.readValue(result.getResponse().getContentAsString(),
                ProjectResourceResponse.class);

        assertNotNull(response);
        verify(projectResourceService, times(1)).create(any(ProjectResourceRequest.class));
    }
}
