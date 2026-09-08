package org.egov.referralmanagement.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkResponse;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectResponse;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearch;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.referralmanagement.TestConfiguration;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.helper.SideEffectRequestTestBuilder;
import org.egov.referralmanagement.helper.SideEffectTestBuilder;
import org.egov.referralmanagement.service.SideEffectService;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ErrorRes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@WebMvcTest(SideEffectApiController.class)
@Import(TestConfiguration.class)
public class SideEffectApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SideEffectService sideEffectService;

    @MockBean
    private Producer producer;

    @MockBean
    ReferralManagementConfiguration referralManagementConfiguration;

    @Test
    @DisplayName("should create side effect and return with 202 accepted")
    void shouldCreateSideEffectAndReturnWith202Accepted() throws Exception {
        SideEffectRequest request = SideEffectRequestTestBuilder.builder()
                .withOneSideEffect()
                .withApiOperationNotUpdate()
                .build();
        List<SideEffect> sideEffects = getSideEffects();
        Mockito.when(sideEffectService.create(ArgumentMatchers.any(SideEffectRequest.class))).thenReturn(sideEffects.get(0));

        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/side-effect/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        SideEffectResponse response = objectMapper.readValue(responseStr, SideEffectResponse.class);

        Assertions.assertNotNull(response.getSideEffect());
        Assertions.assertNotNull(response.getSideEffect().getId());
        Assertions.assertEquals("successful", response.getResponseInfo().getStatus());
    }

    private List<SideEffect> getSideEffects() {
        SideEffect sideEffect = SideEffectTestBuilder.builder().withId().build();
        List<SideEffect> sideEffects = new ArrayList<>();
        sideEffects.add(sideEffect);
        return sideEffects;
    }


    @Test
    @DisplayName("should send error response with error details with 400 bad request for create")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForCreate() throws Exception {
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/side-effect/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SideEffectRequestTestBuilder.builder()
                                .withOneSideEffect()
                                .withBadTenantIdInOneSideEffect()
                                .build())))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr, ErrorRes.class);

        Assertions.assertEquals(1, response.getErrors().size());
        Assertions.assertTrue(response.getErrors().get(0).getCode().contains("tenantId"));
    }

    @Test
    @DisplayName("should update side effect and return with 202 accepted")
    void shouldUpdateSideEffectAndReturnWith202Accepted() throws Exception {
        SideEffectRequest request = SideEffectRequestTestBuilder.builder()
                .withOneSideEffectHavingId()
                .withApiOperationNotNullAndNotCreate()
                .build();
        SideEffect sideEffect = SideEffectTestBuilder.builder().withId().build();
        Mockito.when(sideEffectService.update(ArgumentMatchers.any(SideEffectRequest.class))).thenReturn(sideEffect);

        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/side-effect/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        SideEffectResponse response = objectMapper.readValue(responseStr, SideEffectResponse.class);

        Assertions.assertNotNull(response.getSideEffect());
        Assertions.assertNotNull(response.getSideEffect().getId());
        Assertions.assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("should send error response with error details with 400 bad request for update")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForUpdate() throws Exception {
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/side-effect/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SideEffectRequestTestBuilder.builder()
                                .withOneSideEffectHavingId()
                                .withBadTenantIdInOneSideEffect()
                                .build())))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        Assertions.assertEquals(1, response.getErrors().size());
        Assertions.assertTrue(response.getErrors().get(0).getCode().contains("tenantId"));
    }
    
    @Test
    @DisplayName("Should accept search request and return response as accepted")
    void shouldAcceptSearchRequestAndReturnSideEffect() throws Exception {

        SideEffectSearchRequest sideEffectSearchRequest = SideEffectSearchRequest.builder().sideEffect(
                SideEffectSearch.builder().taskId(Collections.singletonList("some-task-id")).build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        Mockito.when(sideEffectService.search(ArgumentMatchers.any(SideEffectSearchRequest.class),
                ArgumentMatchers.any(Integer.class),
                ArgumentMatchers.any(Integer.class),
                ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(Long.class),
                ArgumentMatchers.any(Boolean.class))).thenReturn(
                        SearchResponse.<SideEffect>builder()
                                .response(Arrays.asList(SideEffectTestBuilder.builder().withId().withAuditDetails().build()))
                                .build());

        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post(
                        "/side-effect/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sideEffectSearchRequest)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        SideEffectBulkResponse response = objectMapper.readValue(responseStr,
                SideEffectBulkResponse.class);

        Assertions.assertEquals(response.getSideEffects().size(), 1);
    }

    @Test
    @DisplayName("Should accept search request and return response as accepted")
    void shouldThrowExceptionIfNoResultFound() throws Exception {

        SideEffectSearchRequest sideEffectSearchRequest = SideEffectSearchRequest.builder().sideEffect(
                SideEffectSearch.builder().taskId(Collections.singletonList("some-task-id")).build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        Mockito.when(sideEffectService.search(ArgumentMatchers.any(SideEffectSearchRequest.class),
                ArgumentMatchers.any(Integer.class),
                ArgumentMatchers.any(Integer.class),
                ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(Long.class),
                ArgumentMatchers.any(Boolean.class))).thenThrow(new CustomException("NO_RESULT_FOUND", "No Side Effect found."));


        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/side-effect/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sideEffectSearchRequest)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        Assertions.assertEquals(response.getErrors().size(), 1);
    }

}
