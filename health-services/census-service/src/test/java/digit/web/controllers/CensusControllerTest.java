package digit.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.service.CensusService;
import digit.web.models.CensusRequest;
import digit.web.models.CensusSearchRequest;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 * API tests for CensusApiController
 */
@Ignore
@RunWith(SpringRunner.class)
@WebMvcTest(CensusController.class)
@Import(TestConfiguration.class)
public class CensusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private CensusService service;

    @Autowired
    private ObjectMapper objectMapper;


    @Test
    public void censusCreatePostSuccess() throws Exception {
        CensusRequest request = CensusRequest.builder().build();
        mockMvc.perform(post("/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    public void censusSearchPostSuccess() throws Exception {
        CensusSearchRequest request = CensusSearchRequest.builder().build();
        mockMvc.perform(post("/_search").contentType(MediaType
                        .APPLICATION_JSON_UTF8).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    public void censusUpdatePostSuccess() throws Exception {
        CensusRequest request = CensusRequest.builder().build();
        mockMvc.perform(post("/_update").contentType(MediaType
                        .APPLICATION_JSON_UTF8).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

}
