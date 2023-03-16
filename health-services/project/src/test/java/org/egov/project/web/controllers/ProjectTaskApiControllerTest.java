package org.egov.project.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.project.TaskRequest;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.models.project.TaskSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.project.TestConfiguration;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.helper.TaskRequestTestBuilder;
import org.egov.project.service.ProjectBeneficiaryService;
import org.egov.project.service.ProjectFacilityService;
import org.egov.project.service.ProjectService;
import org.egov.project.service.ProjectStaffService;
import org.egov.project.service.ProjectTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectApiController.class)
@Import(TestConfiguration.class)
class ProjectTaskApiControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectTaskService projectTaskService;

    @MockBean
    private ProjectFacilityService projectFacilityService;

    @MockBean
    private ProjectStaffService projectStaffService;

    @MockBean
    private ProjectBeneficiaryService projectBeneficiaryService;

    @MockBean
    private Producer producer;

    @MockBean
    private ProjectConfiguration projectConfiguration;

    @MockBean
    private ProjectService projectService;

    @Test
    @DisplayName("should project task create request pass if API operation is create")
    void shouldProjectTaskCreateRequestPassIfApiOperationIsCreate() throws Exception {
        TaskRequest request = TaskRequestTestBuilder.builder().withTask().withRequestInfo().build();
        mockMvc.perform(post("/task/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }


    @Test
    @DisplayName("should pass project task search request if all the required query parameters are present")
    void shouldPassSearchRequestIfQueryParamsArePresent() throws Exception {
        TaskSearchRequest taskSearchRequest = TaskSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .task(TaskSearch.builder().build()).build();
        when(projectTaskService.search(any(TaskSearch.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/task/v1/_search?limit=10&offset=0&tenantId=default").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(taskSearchRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should fail project task search request fail if the required query parameters are missing")
    void shouldFailSearchRequestIfQueryParamsAreMissing() throws Exception {
        TaskSearchRequest taskSearchRequest = TaskSearchRequest.builder()
                .requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build())
                .task(TaskSearch.builder().build()).build();
        when(projectTaskService.search(any(TaskSearch.class), anyInt(),
                anyInt(), anyString(), anyLong(), anyBoolean())).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/task/v1/_search?limit=10&offset=0").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(taskSearchRequest)))
                .andExpect(status().isBadRequest());
    }
}
