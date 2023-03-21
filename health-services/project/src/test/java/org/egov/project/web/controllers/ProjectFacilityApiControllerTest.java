package org.egov.project.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.common.models.project.ProjectFacilityBulkResponse;
import org.egov.common.models.project.ProjectFacilityRequest;
import org.egov.common.models.project.ProjectFacilityResponse;
import org.egov.common.producer.Producer;
import org.egov.project.TestConfiguration;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.ProjectFacilityBulkRequestTestBuilder;
import org.egov.project.helper.ProjectFacilityRequestTestBuilder;
import org.egov.project.helper.ProjectFacilityTestBuilder;
import org.egov.project.service.ProjectBeneficiaryService;
import org.egov.project.service.ProjectFacilityService;
import org.egov.project.service.ProjectService;
import org.egov.project.service.ProjectStaffService;
import org.egov.project.service.ProjectTaskService;
import org.egov.project.web.models.ProjectFacilitySearch;
import org.egov.project.web.models.ProjectFacilitySearchRequest;
import org.egov.tracer.model.ErrorRes;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for ProjectApiController
*/
@WebMvcTest(ProjectApiController.class)
@Import(TestConfiguration.class)
class ProjectFacilityApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectStaffService projectStaffService;

    @MockBean
    private ProjectFacilityService projectFacilityService;

    @MockBean
    private ProjectTaskService projectTaskService;

    @MockBean
    private ProjectBeneficiaryService projectBeneficiaryService;

    @MockBean
    private Producer producer;

    @MockBean
    private ProjectConfiguration projectConfiguration;

    @MockBean
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        when(projectConfiguration.getBulkCreateProjectFacilityTopic())
                .thenReturn("project-facility-bulk-create-topic");
        when(projectConfiguration.getBulkUpdateProjectFacilityTopic())
                .thenReturn("project-facility-bulk-update-topic");
        when(projectConfiguration.getBulkDeleteProjectFacilityTopic())
                .thenReturn("project-facility-bulk-delete-topic");
    }

    @Test
    @DisplayName("should create project facility and return with 202 accepted")
    void shouldCreateProjectFacilityAndReturnWith202Accepted() throws Exception {
        ProjectFacilityRequest request = ProjectFacilityRequestTestBuilder.builder()
                .withOneProjectFacility().build();
        ProjectFacility projectFacility = ProjectFacilityTestBuilder.builder().withId().build();
        List<ProjectFacility> projectFacilityList = new ArrayList<>();
        projectFacilityList.add(projectFacility);
        when(projectFacilityService.create(any(ProjectFacilityRequest.class))).thenReturn(projectFacilityList.get(0));

        final MvcResult result = mockMvc.perform(post("/facility/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ProjectFacilityResponse response = objectMapper.readValue(responseStr, ProjectFacilityResponse.class);

        assertNotNull(response.getProjectFacility().getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("should send error response with error details with 400 bad request for create")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForCreate() throws Exception {
        ProjectFacilityRequest request = ProjectFacilityRequestTestBuilder.builder()
                .withOneProjectFacility()
                .withBadTenantIdInOneProjectFacility()
                .build();
        final MvcResult result = mockMvc.perform(post("/facility/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr, ErrorRes.class);

        assertEquals(1, response.getErrors().size());
        assertTrue(response.getErrors().get(0).getCode().contains("tenantId"));
    }


    @Test
    @DisplayName("should update project facility and return with 202 accepted")
    void shouldUpdateProjectFacilityAndReturnWith202Accepted() throws Exception {
        ProjectFacilityRequest request = ProjectFacilityRequestTestBuilder.builder()
                .withOneProjectFacilityHavingId()
                .build();
        ProjectFacility projectFacility = ProjectFacilityTestBuilder.builder().withId().build();
        List<ProjectFacility> projectFacilityList = new ArrayList<>();
        projectFacilityList.add(projectFacility);
        when(projectFacilityService.update(any(ProjectFacilityRequest.class))).thenReturn(projectFacilityList.get(0));

        final MvcResult result = mockMvc.perform(post("/facility/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ProjectFacilityResponse response = objectMapper.readValue(responseStr, ProjectFacilityResponse.class);

        assertNotNull(response.getProjectFacility().getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }


    @Test
    @DisplayName("should send error response with error details with 400 bad request for update")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForUpdate() throws Exception {
        final MvcResult result = mockMvc.perform(post("/facility/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProjectFacilityRequestTestBuilder.builder()
                                .withOneProjectFacilityHavingId()
                                .withBadTenantIdInOneProjectFacility()
                                .build())))
                .andExpect(status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(1, response.getErrors().size());
        assertTrue(response.getErrors().get(0).getCode().contains("tenantId"));
    }

    @Test
    @DisplayName("Should accept search request and return response as accepted")
    void shouldAcceptSearchRequestAndReturnProjectFacility() throws Exception {

        ProjectFacilitySearchRequest projectFacilitySearchRequest = ProjectFacilitySearchRequest
                .builder().projectFacility(
                ProjectFacilitySearch.builder().projectId(Collections.singletonList("12")).build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(projectFacilityService.search(any(ProjectFacilitySearchRequest.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(Long.class),
                any(Boolean.class))).thenReturn(Arrays.asList(ProjectFacilityTestBuilder.builder()
                .withId().withAuditDetails().build()));

        final MvcResult result = mockMvc.perform(post(
                        "/facility/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectFacilitySearchRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ProjectFacilityBulkResponse response = objectMapper.readValue(responseStr,
                ProjectFacilityBulkResponse.class);

        assertEquals(response.getProjectFacilities().size(), 1);
    }

    @Test
    @DisplayName("should delete project facility and return with 202 accepted")
    void shouldDeleteProjectFacilityAndReturnWith202Accepted() throws Exception {
        ProjectFacilityRequest request = ProjectFacilityRequestTestBuilder.builder()
                .withOneProjectFacilityHavingId()
                .build();
        ProjectFacility projectFacility = ProjectFacilityTestBuilder.builder().withId().build();
        List<ProjectFacility> projectFacilityList = new ArrayList<>();
        projectFacilityList.add(projectFacility);
        when(projectFacilityService.delete(any(ProjectFacilityRequest.class))).thenReturn(projectFacilityList.get(0));

        final MvcResult result = mockMvc.perform(post("/facility/v1/_delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ProjectFacilityResponse response = objectMapper.readValue(responseStr, ProjectFacilityResponse.class);

        assertNotNull(response.getProjectFacility().getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }


    @Test
    @DisplayName("should send error response with error details with 400 bad request for delete")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForDelete() throws Exception {
        final MvcResult result = mockMvc.perform(post("/facility/v1/_delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProjectFacilityRequestTestBuilder.builder()
                                .withOneProjectFacilityHavingId()
                                .withBadTenantIdInOneProjectFacility()
                                .build())))
                .andExpect(status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(1, response.getErrors().size());
        assertTrue(response.getErrors().get(0).getCode().contains("tenantId"));
    }

    @Test
    @DisplayName("should send project facility bulk create request to kafka")
    void shouldSendProjectFacilityToKafkaForBulkCreateRequest() throws Exception {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();

        mockMvc.perform(post("/facility/v1/bulk/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("project-facility-bulk-create-topic"),
                any(ProjectFacilityBulkRequest.class));
        verify(projectConfiguration, times(1)).getBulkCreateProjectFacilityTopic();
    }

    @Test
    @DisplayName("should send project facility bulk update request to kafka")
    void shouldSendProjectFacilityToKafkaForBulkUpdateRequest() throws Exception {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacility()
                .withRequestInfo().build();

        mockMvc.perform(post("/facility/v1/bulk/_update").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("project-facility-bulk-update-topic"),
                any(ProjectFacilityBulkRequest.class));
        verify(projectConfiguration, times(1)).getBulkUpdateProjectFacilityTopic();
    }

    @Test
    @DisplayName("should send project facility bulk delete request to kafka")
    void shouldSendProjectFacilityToKafkaForBulkDeleteRequest() throws Exception {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();

        mockMvc.perform(post("/facility/v1/bulk/_delete").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted()).andReturn();

        verify(producer, times(1)).push(eq("project-facility-bulk-delete-topic"),
                any(ProjectFacilityBulkRequest.class));
        verify(projectConfiguration, times(1)).getBulkDeleteProjectFacilityTopic();
    }
}
