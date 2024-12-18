package org.egov.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.MusterRollMain;
import org.egov.TestConfiguration;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.helper.MusterRollRequestBuilderTest;
import org.egov.repository.MusterRollRepository;
import org.egov.service.MusterRollService;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ErrorRes;
import org.egov.util.ResponseInfoCreator;
import org.egov.web.models.MusterRollRequest;
import org.egov.web.models.MusterRollResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
* API tests for MusterRollApiController
*/


@ContextConfiguration(classes= MusterRollMain.class)
@WebMvcTest(MusterRollApiController.class)
@Import({TestConfiguration.class})
@AutoConfigureMockMvc
class MusterRollApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MusterRollService musterRollService;

    @MockBean
    private ResponseInfoCreator responseInfoCreator;

    @MockBean
    private MusterRollRepository musterRollRepository;


    @Test
    void musterRollV1EstimatePostSuccess() throws Exception {

        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateSuccess();
        ResponseInfo responseInfo = MusterRollRequestBuilderTest.builder().getResponseInfo_Success();
        when(musterRollService.estimateMusterRoll(any(MusterRollRequest.class))).thenReturn(musterRollRequest);
        when(responseInfoCreator.createResponseInfoFromRequestInfo(any(RequestInfo.class), eq(true)))
                .thenReturn(responseInfo);
        ObjectMapper objectMapper = new ObjectMapper();
        String content = objectMapper.writeValueAsString(musterRollRequest);
        MvcResult result = mockMvc.perform(post("/v1/_estimate").contentType(MediaType
                        .APPLICATION_JSON_UTF8).content(content))
                .andExpect(status().isOk()).andReturn();
        String responseStr = result.getResponse().getContentAsString();
        MusterRollResponse response = objectMapper.readValue(responseStr,
                MusterRollResponse.class);
        assertEquals(1, response.getMusterRolls().size());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
     void musterRollV1EstimatePostFailure() throws Exception {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateException();
        ResponseInfo responseInfo = MusterRollRequestBuilderTest.builder().getResponseInfo_Success();
        when(musterRollService.estimateMusterRoll(any(MusterRollRequest.class))).thenThrow(new CustomException("END_DATE_EMPTY","EndDate is mandatory"));
        when(responseInfoCreator.createResponseInfoFromRequestInfo(any(RequestInfo.class), eq(true)))
                .thenReturn(responseInfo);
        ObjectMapper objectMapper = new ObjectMapper();
        String content = objectMapper.writeValueAsString(musterRollRequest);
        MvcResult result = mockMvc.perform(post("/v1/_estimate").contentType(MediaType
                        .APPLICATION_JSON_UTF8).content(content))
                .andExpect(status().isBadRequest()).andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response  = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(1, response.getErrors().size());
        assertEquals("END_DATE_EMPTY",response.getErrors().get(0).getCode());
    }

    @Test
     void musterRollV1CreatePostSuccess() throws Exception {

        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateSuccess();
        ResponseInfo responseInfo = MusterRollRequestBuilderTest.builder().getResponseInfo_Success();
        when(musterRollService.createMusterRoll(any(MusterRollRequest.class))).thenReturn(musterRollRequest);
        when(responseInfoCreator.createResponseInfoFromRequestInfo(any(RequestInfo.class), eq(true)))
                .thenReturn(responseInfo);
        ObjectMapper objectMapper = new ObjectMapper();
        String content = objectMapper.writeValueAsString(musterRollRequest);
        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
        .APPLICATION_JSON_UTF8).content(content))
                .andExpect(status().isOk()).andReturn();
        String responseStr = result.getResponse().getContentAsString();
        MusterRollResponse response = objectMapper.readValue(responseStr,
                MusterRollResponse.class);

        assertEquals(1, response.getMusterRolls().size());
        assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
     void musterRollV1CreatePostFailure() throws Exception {
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateException();
        ResponseInfo responseInfo = MusterRollRequestBuilderTest.builder().getResponseInfo_Success();
        when(musterRollService.createMusterRoll(any(MusterRollRequest.class))).thenThrow(new CustomException("DUPLICATE_MUSTER_ROLL","Muster roll already exists for this register and date"));
        when(responseInfoCreator.createResponseInfoFromRequestInfo(any(RequestInfo.class), eq(true)))
                .thenReturn(responseInfo);
        ObjectMapper objectMapper = new ObjectMapper();
        String content = objectMapper.writeValueAsString(musterRollRequest);
        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON_UTF8).content(content))
                .andExpect(status().isBadRequest()).andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response  = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals(1, response.getErrors().size());
        assertEquals("DUPLICATE_MUSTER_ROLL",response.getErrors().get(0).getCode());
    }

}
