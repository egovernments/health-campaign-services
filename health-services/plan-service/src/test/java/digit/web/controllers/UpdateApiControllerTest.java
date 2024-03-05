package digit.web.controllers;


import org.junit.Ignore;
import org.junit.jupiter.api.Test;
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
* API tests for UpdateApiController
*/
@Ignore
@RunWith(SpringRunner.class)
@WebMvcTest(UpdateApiController.class)
@Import(TestConfiguration.class)
public class UpdateApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void updatePostSuccess() throws Exception {
        mockMvc.perform(post("/plan/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isOk());
    }

    @Test
    public void updatePostFailure() throws Exception {
        mockMvc.perform(post("/plan/_update").contentType(MediaType
        .APPLICATION_JSON_UTF8))
        .andExpect(status().isBadRequest());
    }

}
