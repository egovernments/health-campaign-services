package digit.web.controllers;

import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import digit.TestConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for GeopodeApiController
*/
@Ignore
@RunWith(SpringRunner.class)
@WebMvcTest(GeopodeApiController.class)
@Import(TestConfiguration.class)
public class GeopodeApiControllerTest {

    private MockMvc mockMvc;

    public GeopodeApiControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    public void geopodeBoundaryCreatePostSuccess() throws Exception {
        mockMvc.perform(post("/geopode/boundary/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void geopodeBoundaryCreatePostFailure() throws Exception {
        mockMvc.perform(post("/geopode/boundary/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

}
