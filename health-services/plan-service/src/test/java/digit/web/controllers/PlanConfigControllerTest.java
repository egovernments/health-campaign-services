package digit.web.controllers;

import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import digit.TestConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for ConfigApiController
*/
@Ignore
@RunWith(SpringRunner.class)
@WebMvcTest(PlanConfigController.class)
@Import(TestConfiguration.class)
public class PlanConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void configCreatePostSuccess() throws Exception {
        mockMvc.perform(post("/plan/config/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void configCreatePostFailure() throws Exception {
        mockMvc.perform(post("/plan/config/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void configSearchPostSuccess() throws Exception {
        mockMvc.perform(post("/plan/config/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void configSearchPostFailure() throws Exception {
        mockMvc.perform(post("/plan/config/_search").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

    @Test
    public void configUpdatePostSuccess() throws Exception {
        mockMvc.perform(post("/plan/config/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void configUpdatePostFailure() throws Exception {
        mockMvc.perform(post("/plan/config/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

}
