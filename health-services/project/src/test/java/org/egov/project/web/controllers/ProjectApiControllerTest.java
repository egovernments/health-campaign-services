package org.egov.project.web.controllers;

import org.egov.project.TestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for ProjectApiController
*/
@WebMvcTest(ProjectApiController.class)
@Import(TestConfiguration.class)
public class ProjectApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void projectBeneficiaryV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/project/beneficiary/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectBeneficiaryV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/project/beneficiary/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectBeneficiaryV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/project/beneficiary/v1/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectBeneficiaryV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/project/beneficiary/v1/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectBeneficiaryV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/project/beneficiary/v1/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectBeneficiaryV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/project/beneficiary/v1/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectFacilityV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/project/facility/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectFacilityV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/project/facility/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectFacilityV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/project/facility/v1/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectFacilityV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/project/facility/v1/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectFacilityV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/project/facility/v1/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectFacilityV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/project/facility/v1/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectResourceV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/project/resource/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectResourceV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/project/resource/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectResourceV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/project/resource/v1/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectResourceV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/project/resource/v1/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectResourceV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/project/resource/v1/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectResourceV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/project/resource/v1/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectStaffV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/project/staff/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectStaffV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/project/staff/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectStaffV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/project/staff/v1/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectStaffV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/project/staff/v1/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectStaffV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/project/staff/v1/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectStaffV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/project/staff/v1/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectTaskV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/project/task/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectTaskV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/project/task/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectTaskV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/project/task/v1/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectTaskV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/project/task/v1/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectTaskV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/project/task/v1/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectTaskV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/project/task/v1/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/project/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/project/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/project/v1/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/project/v1/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void projectV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/project/v1/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void projectV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/project/v1/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

}
