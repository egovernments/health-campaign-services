package org.egov.referralmanagement.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkResponse;
import org.egov.common.models.referralmanagement.ReferralRequest;
import org.egov.common.models.referralmanagement.ReferralResponse;
import org.egov.common.models.referralmanagement.ReferralSearch;
import org.egov.common.models.referralmanagement.ReferralSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.referralmanagement.TestConfiguration;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.helper.ReferralRequestTestBuilder;
import org.egov.referralmanagement.helper.ReferralTestBuilder;
import org.egov.referralmanagement.service.ReferralManagementService;
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
import java.util.List;

@WebMvcTest(ReferralManagementApiController.class)
@Import(TestConfiguration.class)
public class ReferralManagementApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReferralManagementService referralManagementService;

    @MockBean
    private Producer producer;

    @MockBean
    ReferralManagementConfiguration referralManagementConfiguration;

    @Test
    @DisplayName("should create referral and return with 202 accepted")
    void shouldCreateReferralAndReturnWith202Accepted() throws Exception {
        ReferralRequest request = ReferralRequestTestBuilder.builder()
                .withOneReferral()
                .withApiOperationNotUpdate()
                .build();
        List<Referral> referrals = getReferrals();
        Mockito.when(referralManagementService.create(ArgumentMatchers.any(ReferralRequest.class))).thenReturn(referrals.get(0));

        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ReferralResponse response = objectMapper.readValue(responseStr, ReferralResponse.class);

        Assertions.assertNotNull(response.getReferral());
        Assertions.assertNotNull(response.getReferral().getId());
        Assertions.assertEquals("successful", response.getResponseInfo().getStatus());
    }

    private List<Referral> getReferrals() {
        Referral referral = ReferralTestBuilder.builder().withId().build();
        List<Referral> referrals = new ArrayList<>();
        referrals.add(referral);
        return referrals;
    }


    @Test
    @DisplayName("should send error response with error details with 400 bad request for create")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForCreate() throws Exception {
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/v1/_create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ReferralRequestTestBuilder.builder()
                                .withOneReferral()
                                .withBadTenantIdInOneReferral()
                                .build())))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr, ErrorRes.class);

        Assertions.assertEquals(1, response.getErrors().size());
        Assertions.assertTrue(response.getErrors().get(0).getCode().contains("tenantId"));
    }

    @Test
    @DisplayName("should update referral and return with 202 accepted")
    void shouldUpdateReferralAndReturnWith202Accepted() throws Exception {
        ReferralRequest request = ReferralRequestTestBuilder.builder()
                .withOneReferralHavingId()
                .withApiOperationNotNullAndNotCreate()
                .build();
        Referral referral = ReferralTestBuilder.builder().withId().build();
        Mockito.when(referralManagementService.update(ArgumentMatchers.any(ReferralRequest.class))).thenReturn(referral);

        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ReferralResponse response = objectMapper.readValue(responseStr, ReferralResponse.class);

        Assertions.assertNotNull(response.getReferral());
        Assertions.assertNotNull(response.getReferral().getId());
        Assertions.assertEquals("successful", response.getResponseInfo().getStatus());
    }

    @Test
    @DisplayName("should send error response with error details with 400 bad request for update")
    void shouldSendErrorResWithErrorDetailsWith400BadRequestForUpdate() throws Exception {
        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/v1/_update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ReferralRequestTestBuilder.builder()
                                .withOneReferralHavingId()
                                .withBadTenantIdInOneReferral()
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
    void shouldAcceptSearchRequestAndReturnReferral() throws Exception {

        ReferralSearchRequest referralSearchRequest = ReferralSearchRequest.builder().referral(
                ReferralSearch.builder().projectBeneficiaryId(Arrays.asList("some-project-beneficiary-id")).build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        Mockito.when(referralManagementService.search(ArgumentMatchers.any(ReferralSearchRequest.class),
                ArgumentMatchers.any(Integer.class),
                ArgumentMatchers.any(Integer.class),
                ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(Long.class),
                ArgumentMatchers.any(Boolean.class))).thenReturn(
                        SearchResponse.<Referral>builder()
                                .response(Arrays.asList(ReferralTestBuilder.builder().withId().withAuditDetails().build()))
                                .build()
                );

        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post(
                        "/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(referralSearchRequest)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ReferralBulkResponse response = objectMapper.readValue(responseStr,
                ReferralBulkResponse.class);

        Assertions.assertEquals(response.getReferrals().size(), 1);
    }

    @Test
    @DisplayName("Should accept search request and return response as accepted")
    void shouldThrowExceptionIfNoResultFound() throws Exception {

        ReferralSearchRequest referralSearchRequest = ReferralSearchRequest.builder().referral(
                ReferralSearch.builder().projectBeneficiaryId(Arrays.asList("some-project-beneficiary-id")).build()
        ).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        Mockito.when(referralManagementService.search(ArgumentMatchers.any(ReferralSearchRequest.class),
                ArgumentMatchers.any(Integer.class),
                ArgumentMatchers.any(Integer.class),
                ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(Long.class),
                ArgumentMatchers.any(Boolean.class))).thenThrow(new CustomException("NO_RESULT_FOUND", "No Referral found."));


        final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/v1/_search?limit=10&offset=100&tenantId=default&lastChangedSince=1234322&includeDeleted=false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(referralSearchRequest)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        Assertions.assertEquals(response.getErrors().size(), 1);
    }

}
