package org.egov.project.web.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.common.models.project.ProjectResourceRequest;
import org.egov.common.models.project.ProjectResourceResponse;
import org.egov.common.producer.Producer;
import org.egov.project.TestConfiguration;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.ProjectResourceBulkRequestTestBuilder;
import org.egov.project.helper.ProjectResourceRequestTestBuilder;
import org.egov.project.service.ProjectResourceService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectResourceApiController.class)
@Import(TestConfiguration.class)
class ProjectResourceApiControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectResourceService projectResourceService;

    @MockBean
    private Producer producer;

    @MockBean
    private ProjectConfiguration configuration;

    @BeforeEach
    void setUp() {
        when(configuration.getCreateProjectResourceBulkTopic()).thenReturn("save-project-resource-bulk-topic");
        when(configuration.getUpdateProjectResourceBulkTopic()).thenReturn("update-project-resource-bulk-topic");
        when(configuration.getDeleteProjectResourceBulkTopic()).thenReturn("delete-project-resource-bulk-topic");
    }

    @Test
    @DisplayName("should return project resource response for create")
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

    @Test
    @DisplayName("should send project resource bulk create request to kafka")
    void shouldSendProjectResourceToKafkaForBulkCreateRequest() throws Exception {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();

        mockMvc.perform(post("/resource/v1/bulk/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("save-project-resource-bulk-topic"), any(ProjectResourceBulkRequest.class));
        verify(configuration, times(1)).getCreateProjectResourceBulkTopic();
    }

    @Test
    @DisplayName("should return project resource response for update")
    void shouldProjectResourceUpdateRequestPass() throws Exception {
        ProjectResourceRequest projectRequest = ProjectResourceRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();
        MvcResult result = mockMvc.perform(post("/resource/v1/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isAccepted()).andReturn();

        ProjectResourceResponse response = objectMapper.readValue(result.getResponse().getContentAsString(),
                ProjectResourceResponse.class);

        assertNotNull(response);
        verify(projectResourceService, times(1)).update(any(ProjectResourceRequest.class));
    }

    @Test
    @DisplayName("should send project resource bulk update request to kafka")
    void shouldSendProjectResourceToKafkaForBulkUpdateRequest() throws Exception {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();

        mockMvc.perform(post("/resource/v1/bulk/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("update-project-resource-bulk-topic"), any(ProjectResourceBulkRequest.class));
        verify(configuration, times(1)).getUpdateProjectResourceBulkTopic();
    }


    @Test
    @DisplayName("should return project resource response for delete")
    void shouldProjectResourceDeleteRequestPass() throws Exception {
        ProjectResourceRequest projectRequest = ProjectResourceRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();
        MvcResult result = mockMvc.perform(post("/resource/v1/_delete").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isAccepted()).andReturn();

        ProjectResourceResponse response = objectMapper.readValue(result.getResponse().getContentAsString(),
                ProjectResourceResponse.class);

        assertNotNull(response);
        verify(projectResourceService, times(1)).delete(any(ProjectResourceRequest.class));
    }

    @Test
    @DisplayName("should send project resource bulk delete request to kafka")
    void shouldSendProjectResourceToKafkaForBulkDeleteRequest() throws Exception {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();

        mockMvc.perform(post("/resource/v1/bulk/_delete").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("delete-project-resource-bulk-topic"), any(ProjectResourceBulkRequest.class));
        verify(configuration, times(1)).getDeleteProjectResourceBulkTopic();
    }
}
