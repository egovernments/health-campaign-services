package org.egov.project.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.BeneficiaryBulkResponse;
import org.egov.common.models.project.BeneficiaryRequest;
import org.egov.common.models.project.BeneficiaryResponse;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.producer.Producer;
import org.egov.project.TestConfiguration;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.BeneficiaryRequestTestBuilder;
import org.egov.project.helper.ProjectBeneficiaryTestBuilder;
import org.egov.project.service.ProjectBeneficiaryService;
import org.egov.project.service.ProjectFacilityService;
import org.egov.project.service.ProjectService;
import org.egov.project.service.ProjectStaffService;
import org.egov.project.service.ProjectTaskService;
import org.egov.project.web.models.BeneficiarySearchRequest;
import org.egov.project.web.models.ProjectBeneficiarySearch;
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
public class ProjectBeneficiaryApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectBeneficiaryService projectBeneficiaryService;

    @MockBean
    private ProjectStaffService projectStaffService;

    @MockBean
    private ProjectTaskService projectTaskService;

    @MockBean
    private ProjectFacilityService projectFacilityService;

    @MockBean
    private Producer producer;

    @MockBean
    private ProjectConfiguration projectConfiguration;

    @MockBean
    private ProjectService projectService;

    @Test
    @DisplayName("should create project beneficiary and return with 202 accepted")
    void shouldCreateProjectBeneficiaryAndReturnWith202Accepted() throws Exception {
        BeneficiaryRequest request = BeneficiaryRequestTestBuilder.builder()
                .withOneProjectBeneficiary()
                .withApiOperationNotUpdate()
                .build();
        List<ProjectBeneficiary> projectBeneficiaries = getProjectBeneficiaries();
        when(projectBeneficiaryService.create(any(BeneficiaryRequest.class))).thenReturn(projectBeneficiaries);

        final MvcResult result = mockMvc.perform(post("/beneficiary/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        BeneficiaryResponse response = objectMapper.readValue(responseStr, BeneficiaryResponse.class);

        assertNotNull(response.getProjectBeneficiary());
        assertNotNull(response.getProjectBeneficiary().getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    private List<ProjectBeneficiary> getProjectBeneficiaries() {
        ProjectBeneficiary projectBeneficiary = ProjectBeneficiaryTestBuilder.builder().withId().build();
        List<ProjectBeneficiary> projectBeneficiaries = new ArrayList<>();
        projectBeneficiaries.add(projectBeneficiary);
        return projectBeneficiaries;
    }


    @Test
    @DisplayName("should send error response with error details with 400 bad request for create")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForCreate() throws Exception {
        final MvcResult result = mockMvc.perform(post("/beneficiary/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BeneficiaryRequestTestBuilder.builder()
                                .withOneProjectBeneficiary()
                                .withBadTenantIdInOneProjectBeneficiary()
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
        final MvcResult result = mockMvc.perform(post("/beneficiary/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BeneficiaryRequestTestBuilder.builder()
                                .withOneProjectBeneficiary()
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
    @DisplayName("should update project beneficiary and return with 202 accepted")
    void shouldUpdateProjectBeneficiaryAndReturnWith202Accepted() throws Exception {
        BeneficiaryRequest request = BeneficiaryRequestTestBuilder.builder()
                .withOneProjectBeneficiaryHavingId()
                .withApiOperationNotNullAndNotCreate()
                .build();
        ProjectBeneficiary projectBeneficiary = ProjectBeneficiaryTestBuilder.builder().withId().build();
        List<ProjectBeneficiary> projectBeneficiaries = new ArrayList<>();
        projectBeneficiaries.add(projectBeneficiary);
        when(projectBeneficiaryService.update(any(BeneficiaryRequest.class))).thenReturn(projectBeneficiaries);

        final MvcResult result = mockMvc.perform(post("/beneficiary/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        BeneficiaryResponse response = objectMapper.readValue(responseStr, BeneficiaryResponse.class);

        assertNotNull(response.getProjectBeneficiary());
        assertNotNull(response.getProjectBeneficiary().getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("should send error response with error details with 400 bad request for update")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForUpdate() throws Exception {
        final MvcResult result = mockMvc.perform(post("/beneficiary/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BeneficiaryRequestTestBuilder.builder()
                                .withOneProjectBeneficiaryHavingId()
                                .withBadTenantIdInOneProjectBeneficiary()
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
        final MvcResult result = mockMvc.perform(post("/beneficiary/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BeneficiaryRequestTestBuilder.builder()
                                .withOneProjectBeneficiaryHavingId()
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

        BeneficiarySearchRequest beneficiarySearchRequest = BeneficiarySearchRequest.builder().projectBeneficiary(
                ProjectBeneficiarySearch.builder().projectId("12").build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        when(projectBeneficiaryService.search(any(BeneficiarySearchRequest.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(Long.class),
                any(Boolean.class))).thenReturn(Arrays.asList(ProjectBeneficiaryTestBuilder.builder().withId().withAuditDetails().build()));

        final MvcResult result = mockMvc.perform(post(
                        "/beneficiary/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(beneficiarySearchRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        BeneficiaryBulkResponse response = objectMapper.readValue(responseStr,
                BeneficiaryBulkResponse.class);

        assertEquals(response.getProjectBeneficiaries().size(), 1);
    }

    @Test
    @DisplayName("Should accept search request and return response as accepted")
    void shouldThrowExceptionIfNoResultFound() throws Exception {

        BeneficiarySearchRequest beneficiarySearchRequest = BeneficiarySearchRequest.builder().projectBeneficiary(
                ProjectBeneficiarySearch.builder().projectId("12").build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        when(projectBeneficiaryService.search(any(BeneficiarySearchRequest.class),
                any(Integer.class),
                any(Integer.class),
                any(String.class),
                any(Long.class),
                any(Boolean.class))).thenThrow(new CustomException("NO_RESULT_FOUND", "No project found."));


        final MvcResult result = mockMvc.perform(post("/beneficiary/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(beneficiarySearchRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(response.getErrors().size(), 1);
    }

}
