package org.egov.project.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.project.TestConfiguration;
import org.egov.project.helper.TaskRequestTestBuilder;
import org.egov.project.service.ProjectStaffService;
import org.egov.project.service.ProjectTaskService;
import org.egov.project.web.models.TaskRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
    private ProjectStaffService projectStaffService;

    @Test
    @DisplayName("should project task create request fail if API operation is update")
    void shouldProjectTaskCreateRequestFailIfApiOperationIsUpdate() throws Exception {
        TaskRequest request = TaskRequestTestBuilder.builder().withTask().withRequestInfo().withApiOperationUpdate().build();

        mockMvc.perform(post("/task/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should project task create request fail if API operation is delete")
    void shouldProjectTaskCreateRequestFailIfApiOperationIsDelete() throws Exception {
        TaskRequest request = TaskRequestTestBuilder.builder().withTask().withRequestInfo().withApiOperationDelete().build();

        mockMvc.perform(post("/task/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should project task create request pass if API operation is create")
    void shouldProjectTaskCreateRequestPassIfApiOperationIsCreate() throws Exception {
        TaskRequest request = TaskRequestTestBuilder.builder().withTask().withRequestInfo().withApiOperationCreate().build();

        mockMvc.perform(post("/task/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }
}
