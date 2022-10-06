package org.digit.health.sync.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.digit.health.sync.helper.SyncUpRequestTestBuilder;
import org.digit.health.sync.service.FileSyncService;
import org.digit.health.sync.web.models.SyncId;
import org.digit.health.sync.web.models.request.SyncUpDto;
import org.digit.health.sync.web.models.request.SyncUpRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SyncController.class)
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileSyncService fileSyncService;

    @Test
    @DisplayName("should return Http status as 202 and sync id on sync request submission")
    void shouldReturnHttpStatus202AndSyncIdOnSuccessfulRequestSubmission() throws Exception {
        SyncUpRequest syncUpRequest = SyncUpRequestTestBuilder.builder()
                .withFileDetails()
                .build();

        when(fileSyncService.syncUp(any(SyncUpDto.class))).thenReturn(SyncId.builder().syncId("id").build());
        mockMvc.perform(post("/sync/v1/up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncUpRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.syncId")
                        .value("id"));
    }

    @Disabled
    @Test
    @DisplayName("should return Http status as 400 in case of a invalid file")
    void shouldReturnHttpStatus400InCaseOfAInvalidFile() throws Exception {
        SyncUpRequest syncUpRequest = SyncUpRequestTestBuilder.builder()
                .withFileDetails()
                .build();

        when(fileSyncService.syncUp(any(SyncUpDto.class)))
                .thenThrow(new CustomException("Invalid File","Invalid File"));

        mockMvc.perform(post("/sync/v1/up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncUpRequest)))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.Errors[0].message")
                        .value("Invalid File"));
    }
}