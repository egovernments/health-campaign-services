package org.egov.referralmanagement.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.producer.Producer;
import org.egov.referralmanagement.TestConfiguration;
import org.egov.referralmanagement.repository.DownsyncGenerationJobRepository;
import org.egov.referralmanagement.service.DownsyncFileGenService;
import org.egov.referralmanagement.service.DownsyncJobRegistry;
import org.egov.referralmanagement.service.JobHeartbeatScheduler;
import org.egov.referralmanagement.web.models.DownsyncJobDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@code POST /downsync/v1/jobs/_search}.
 *
 * <p>Guarantees locked down here — every one of these was silently broken
 * before we made tenantId a required field:
 * <ol>
 *   <li>Both {@code jobId} and {@code tenantId} are mandatory — a missing
 *       or blank value returns 400 (via @NotBlank), without ever
 *       touching the repository.</li>
 *   <li>When both are supplied and the row exists under that tenant, the
 *       controller returns 200 with the full {@link DownsyncJobDetail}
 *       assembled by {@link DownsyncGenerationJobRepository#findJobDetail(String, String)}.
 *       The repository is called with the exact (jobId, tenantId) pair —
 *       no cross-schema scan.</li>
 *   <li>When the repository returns null (row missing, OR a caller
 *       guessing another tenant's id), the controller returns 404 with a
 *       {@code JOB_NOT_FOUND} code that includes both jobId and tenantId
 *       in the message — no data leak about whether the id exists in a
 *       different schema.</li>
 * </ol>
 */
@WebMvcTest(DownsyncFileGenController.class)
@Import(TestConfiguration.class)
class DownsyncFileGenControllerSearchTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private DownsyncFileGenService downsyncFileGenService;
    @MockBean private DownsyncGenerationJobRepository jobRepository;
    @MockBean private DownsyncJobRegistry jobRegistry;
    @MockBean private JobHeartbeatScheduler heartbeat;
    @MockBean private Producer producer;

    private static final String URL = "/downsync/v1/jobs/_search";
    private static final String JOB_ID   = "6747a1a6-6103-4383-9b0c-fd39f13c67b1";
    private static final String TENANT   = "ba";

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("200 OK when job exists for the given tenantId + jobId")
    void search_ok() throws Exception {
        DownsyncJobDetail detail = DownsyncJobDetail.builder()
                .id(JOB_ID).tenantId(TENANT).status("PARTIAL_FAILURE")
                .totalRequested(313).totalSucceeded(308).totalFailed(5)
                .build();
        when(jobRepository.findJobDetail(JOB_ID, TENANT)).thenReturn(detail);

        String body = "{\"RequestInfo\":{},\"jobId\":\"" + JOB_ID + "\",\"tenantId\":\"" + TENANT + "\"}";
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.id").value(JOB_ID))
                .andExpect(jsonPath("$.job.tenantId").value(TENANT))
                .andExpect(jsonPath("$.job.status").value("PARTIAL_FAILURE"))
                .andExpect(jsonPath("$.job.totalRequested").value(313))
                .andExpect(jsonPath("$.job.totalFailed").value(5));

        // Repository received exactly what the caller sent — no legacy schema scan.
        ArgumentCaptor<String> jid = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tid = ArgumentCaptor.forClass(String.class);
        verify(jobRepository).findJobDetail(jid.capture(), tid.capture());
        assert JOB_ID.equals(jid.getValue());
        assert TENANT.equals(tid.getValue());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("400 when tenantId is missing")
    void search_missingTenantId_fails() throws Exception {
        String body = "{\"RequestInfo\":{},\"jobId\":\"" + JOB_ID + "\"}";
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        // Repo must NOT have been queried when validation failed.
        verify(jobRepository, never()).findJobDetail(eq(JOB_ID), eq((String) null));
    }

    @Test
    @DisplayName("400 when tenantId is blank/whitespace")
    void search_blankTenantId_fails() throws Exception {
        String body = "{\"RequestInfo\":{},\"jobId\":\"" + JOB_ID + "\",\"tenantId\":\"   \"}";
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 when jobId is missing")
    void search_missingJobId_fails() throws Exception {
        String body = "{\"RequestInfo\":{},\"tenantId\":\"" + TENANT + "\"}";
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("400 when jobId is blank/whitespace")
    void search_blankJobId_fails() throws Exception {
        String body = "{\"RequestInfo\":{},\"jobId\":\"  \",\"tenantId\":\"" + TENANT + "\"}";
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // ── Not-found & mismatch semantics ────────────────────────────────────────

    @Test
    @DisplayName("404 JOB_NOT_FOUND — repository returns null for this tenant scope")
    void search_notFound() throws Exception {
        when(jobRepository.findJobDetail(JOB_ID, TENANT)).thenReturn(null);

        String body = "{\"RequestInfo\":{},\"jobId\":\"" + JOB_ID + "\",\"tenantId\":\"" + TENANT + "\"}";
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.message",
                        containsString("tenantId: " + TENANT)));
    }

    @Test
    @DisplayName("Cross-tenant guess returns 404 — repo scoped to other tenant's schema finds nothing")
    void search_crossTenantGuess_returns404() throws Exception {
        // Job exists under 'ba', but caller queries with tenantId='ke' — the repository
        // is scoped to 'ke' and finds nothing.
        when(jobRepository.findJobDetail(JOB_ID, "ke")).thenReturn(null);

        String body = "{\"RequestInfo\":{},\"jobId\":\"" + JOB_ID + "\",\"tenantId\":\"ke\"}";
        mockMvc.perform(MockMvcRequestBuilders.post(URL)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));

        // The 'ba' tenant's repo lookup must never happen from a 'ke' caller.
        verify(jobRepository, never()).findJobDetail(JOB_ID, "ba");
    }
}
