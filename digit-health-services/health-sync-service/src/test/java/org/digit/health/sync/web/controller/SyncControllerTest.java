package org.digit.health.sync.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.digit.health.sync.helper.SyncSearchRequestTestBuilder;
import org.digit.health.sync.helper.SyncUpRequestTestBuilder;
import org.digit.health.sync.service.SyncService;
import org.digit.health.sync.web.models.*;
import org.digit.health.sync.web.models.dao.SyncLogData;
import org.digit.health.sync.web.models.request.SyncLogSearchDto;
import org.digit.health.sync.web.models.request.SyncLogSearchRequest;
import org.digit.health.sync.web.models.request.SyncUpDto;
import org.digit.health.sync.web.models.request.SyncUpRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

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
    @Qualifier("fileSyncService")
    private SyncService syncService;

    @Test
    @DisplayName("should return Http status as 202 and sync id on sync request submission")
    void shouldReturnHttpStatus202AndSyncIdOnSuccessfulRequestSubmission() throws Exception {
        SyncUpRequest syncUpRequest = SyncUpRequestTestBuilder.builder()
                .withFileDetails()
                .build();
        when(syncService.syncUp(any(SyncUpDto.class))).thenReturn(SyncId.builder().syncId("id").build());

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
        when(syncService.syncUp(any(SyncUpDto.class)))
                .thenThrow(new CustomException("Invalid File", "Invalid File"));

        mockMvc.perform(post("/sync/v1/up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncUpRequest)))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.Errors[0].message")
                        .value("Invalid File"));
    }

    @Test
    @DisplayName("should return Http status as 200 and sync result on sync search request")
    void shouldReturnHttpStatus200AndSyncResultOnSearchRequest() throws Exception {
        SyncLogSearchRequest syncLogSearchRequest = SyncSearchRequestTestBuilder.builder().withSyncId().build();
        List<SyncLogData> searchedData = new ArrayList<>();
        SyncLogData responseSync = SyncLogData.builder()
                .status(SyncStatus.CREATED)
                .successCount(0)
                .errorCount(0)
                .totalCount(0)
                .comment("")
                .fileDetails(
                        FileDetails.builder()
                                .fileStoreId("fileId")
                                .checksum("checksum")
                                .build()
                )

                .referenceId(
                        ReferenceId.builder()
                                .id("1")
                                .type("campaign")
                                .build()
                )
                .tenantId("mq")
                .auditDetails(
                        AuditDetails.builder()
                                .createdBy("uid")
                                .createdTime(1L)
                                .lastModifiedBy("uid")
                                .lastModifiedTime(1L).build()
                )
                .build();
        searchedData.add(responseSync);
        when(syncService.find(any(SyncLogSearchDto.class))).thenReturn(searchedData);

        mockMvc.perform(post("/sync/v1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncLogSearchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncs.length()").value(1));
        ;
    }


    @Test
    @DisplayName("should return Http status as 400 and if tenantId is not present in search request")
    void should$eturnHttpStatus400IfTenantIdIsNotPresentInSearchRequest() throws Exception {
        SyncLogSearchRequest syncLogSearchRequest = SyncSearchRequestTestBuilder.builder().build();
        mockMvc.perform(post("/sync/v1/stats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncLogSearchRequest)))
                .andExpect(status().is4xxClientError());
    }

}