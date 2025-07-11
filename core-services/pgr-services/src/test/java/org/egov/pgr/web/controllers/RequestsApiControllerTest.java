package org.egov.pgr.web.controllers;

import org.egov.pgr.TestConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for RequestsApiController
*/
@Disabled
@ExtendWith(MockitoExtension.class)
@WebMvcTest(RequestsApiController.class)
@Import(TestConfiguration.class)
public class RequestsApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void requestsCreatePostSuccess() throws Exception {
        mockMvc.perform(post("/requests/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void requestsCreatePostFailure() throws Exception {
        mockMvc.perform(post("/requests/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void requestsSearchPostSuccess() throws Exception {
        mockMvc.perform(post("/requests/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void requestsSearchPostFailure() throws Exception {
        mockMvc.perform(post("/requests/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void requestsUpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/requests/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void requestsUpdatePostFailure() throws Exception {
        mockMvc.perform(post("/requests/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

}
