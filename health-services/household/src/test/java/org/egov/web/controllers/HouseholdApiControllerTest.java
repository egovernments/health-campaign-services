package org.egov.web.controllers;

import org.egov.TestConfiguration;
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
* API tests for HouseholdApiController
*/
@WebMvcTest(HouseholdApiController.class)
@Import(TestConfiguration.class)
public class HouseholdApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Disabled
    public void householdMemberV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/household/member/v1/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void householdMemberV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/household/member/v1/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void householdMemberV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/household/member/v1/_search").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void householdMemberV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/household/member/v1/_search").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void householdMemberV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/household/member/v1/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void householdMemberV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/household/member/v1/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void householdV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/household/v1/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void householdV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/household/v1/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void householdV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/household/v1/_search").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void householdV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/household/v1/_search").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void householdV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/household/v1/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void householdV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/household/v1/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

}
