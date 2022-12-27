package org.egov.individual.web.controllers;

import org.egov.individual.TestConfiguration;
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
* API tests for IndividualApiController
*/
@WebMvcTest(IndividualApiController.class)
@Import(TestConfiguration.class)
public class IndividualApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Disabled
    public void individualV1CreatePostSuccess() throws Exception {
        mockMvc.perform(post("/individual/v1/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void individualV1CreatePostFailure() throws Exception {
        mockMvc.perform(post("/individual/v1/_create").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void individualV1SearchPostSuccess() throws Exception {
        mockMvc.perform(post("/individual/v1/_search").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void individualV1SearchPostFailure() throws Exception {
        mockMvc.perform(post("/individual/v1/_search").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled
    public void individualV1UpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/individual/v1/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isOk());
    }

    @Test
    @Disabled
    public void individualV1UpdatePostFailure() throws Exception {
        mockMvc.perform(post("/individual/v1/_update").contentType(MediaType
        .APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    }

}
