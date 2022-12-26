package org.egov.project.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.project.TestConfiguration;
import org.egov.project.helper.ProjectStaffRequestTestBuilder;
import org.egov.project.helper.ProjectStaffTestBuilder;
import org.egov.project.service.ProjectStaffService;
import org.egov.project.web.models.ProjectStaff;
import org.egov.project.web.models.ProjectStaffRequest;
import org.egov.project.web.models.ProjectStaffResponse;
import org.egov.project.web.models.ProjectStaffSearch;
import org.egov.project.web.models.ProjectStaffSearchRequest;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ErrorRes;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for ProjectApiController
*/
@WebMvcTest(ProjectApiController.class)
@Import(TestConfiguration.class)
public class ProjectApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectStaffService projectStaffService;


    @Test
    @DisplayName("should create project staff and return with 202 accepted")
    void shouldCreateProjectStaffAndReturnWith202Accepted() throws Exception {
        ProjectStaffRequest request = ProjectStaffRequestTestBuilder.builder()
                .withOneProjectStaff()
                .withApiOperationNotUpdate()
                .build();
        ProjectStaff projectStaff = ProjectStaffTestBuilder.builder().withId().build();
        List<ProjectStaff> projectStaffList = new ArrayList<>();
        projectStaffList.add(projectStaff);
        when(projectStaffService.create(any(ProjectStaffRequest.class))).thenReturn(projectStaffList);

        final MvcResult result = mockMvc.perform(post("/staff/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ProjectStaffResponse response = objectMapper.readValue(responseStr, ProjectStaffResponse.class);

        assertEquals(1, response.getProjectStaff().size());
        assertNotNull(response.getProjectStaff().get(0).getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("should send error response with error details with 400 bad request for create")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForCreate() throws Exception {
        final MvcResult result = mockMvc.perform(post("/staff/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProjectStaffRequestTestBuilder.builder()
                                .withOneProjectStaff()
                                .withBadTenantIdInOneProjectStaff()
                                .build())))
                .andExpect(status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr, ErrorRes.class);

        assertEquals(1, response.getErrors().size());
        assertTrue(response.getErrors().get(0).getCode().contains("tenantId"));
    }


    @Test
    @DisplayName("should send 400 bad request in case of incorrect api operation for create")
    void shouldSend400BadRequestInCaseOfIncorrectApiOperationForCreate() throws Exception {
        final MvcResult result = mockMvc.perform(post("/staff/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProjectStaffRequestTestBuilder.builder()
                                .withOneProjectStaff()
                                .withApiOperationNotNullAndNotCreate()
                                .build())))
                .andExpect(status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(1, response.getErrors().size());
    }


    @Test
    @DisplayName("should update project staff and return with 202 accepted")
    void shouldUpdateProjectStaffAndReturnWith202Accepted() throws Exception {
        ProjectStaffRequest request = ProjectStaffRequestTestBuilder.builder()
                .withOneProjectStaffHavingId()
                .withApiOperationNotNullAndNotCreate()
                .build();
        ProjectStaff projectStaff = ProjectStaffTestBuilder.builder().withId().build();
        List<ProjectStaff> projectStaffList = new ArrayList<>();
        projectStaffList.add(projectStaff);
        when(projectStaffService.update(any(ProjectStaffRequest.class))).thenReturn(projectStaffList);

        final MvcResult result = mockMvc.perform(post("/staff/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ProjectStaffResponse response = objectMapper.readValue(responseStr, ProjectStaffResponse.class);

        assertEquals(1, response.getProjectStaff().size());
        assertNotNull(response.getProjectStaff().get(0).getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }


    @Test
    @DisplayName("should send error response with error details with 400 bad request for update")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForUpdate() throws Exception {
        final MvcResult result = mockMvc.perform(post("/staff/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProjectStaffRequestTestBuilder.builder()
                                .withOneProjectStaffHavingId()
                                .withBadTenantIdInOneProjectStaff()
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
    @DisplayName("should send 400 bad request in case of incorrect api operation for update")
    void shouldSend400BadRequestInCaseOfIncorrectApiOperationForUpdate() throws Exception {
        final MvcResult result = mockMvc.perform(post("/staff/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProjectStaffRequestTestBuilder.builder()
                                .withOneProjectStaffHavingId()
                                .withApiOperationNotUpdate()
                                .build())))
                .andExpect(status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(1, response.getErrors().size());
    }

    @Test
    @DisplayName("Should accept search request and return response as accepted")
    void shouldAcceptSearchRequestAndReturnProjectStaff() throws Exception {

        ProjectStaffSearchRequest projectStaffSearchRequest = ProjectStaffSearchRequest.builder().projectStaff(
                ProjectStaffSearch.builder().projectId("12").build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(projectStaffService.search(any(ProjectStaffSearchRequest.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(Long.class),
                any(Boolean.class))).thenReturn(Arrays.asList(ProjectStaffTestBuilder.builder().withId().withAuditDetails().build()));

        final MvcResult result = mockMvc.perform(post(
                        "/staff/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectStaffSearchRequest)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ProjectStaffResponse response = objectMapper.readValue(responseStr,
                ProjectStaffResponse.class);

        assertEquals(response.getProjectStaff().size(), 1);
    }

    @Test
    @DisplayName("Should accept search request and return response as accepted")
    void shouldThrowExceptionIfNoResultFound() throws Exception {

        ProjectStaffSearchRequest projectStaffSearchRequest = ProjectStaffSearchRequest.builder().projectStaff(
                ProjectStaffSearch.builder().projectId("12").build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();
        when(projectStaffService.search(any(ProjectStaffSearchRequest.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(Long.class),
                any(Boolean.class))).thenThrow(new CustomException("NO_RESULT_FOUND", "No project found."));


        final MvcResult result = mockMvc.perform(post("/staff/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectStaffSearchRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(response.getErrors().size(), 1);
    }

}
