package org.egov.facility.web.controllers;

import org.egov.facility.TestConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for FacilityApiController
*/
@WebMvcTest(FacilityApiController.class)
@Import(TestConfiguration.class)
public class FacilityApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Disabled
    public void facilityV1BulkCreatePostSuccess() throws Exception {
        mockMvc.perform(post("/facility/v1/bulk/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void facilityV1BulkCreatePostFailure() throws Exception {
        mockMvc.perform(post("/facility/v1/bulk/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void facilityV1BulkDeletePostSuccess() throws Exception {
        mockMvc.perform(post("/facility/v1/bulk/_delete").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void facilityV1BulkDeletePostFailure() throws Exception {
        mockMvc.perform(post("/facility/v1/bulk/_delete").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void facilityV1BulkUpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/facility/v1/bulk/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void facilityV1BulkUpdatePostFailure() throws Exception {
        mockMvc.perform(post("/facility/v1/bulk/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void facilityV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/facility/v1/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void facilityV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/facility/v1/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void facilityV1DeletePostSuccess() throws Exception {
        mockMvc.perform(post("/facility/v1/_delete").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void facilityV1DeletePostFailure() throws Exception {
        mockMvc.perform(post("/facility/v1/_delete").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void facilityV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/facility/v1/_search").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void facilityV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/facility/v1/_search").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void facilityV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/facility/v1/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void facilityV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/facility/v1/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

}
