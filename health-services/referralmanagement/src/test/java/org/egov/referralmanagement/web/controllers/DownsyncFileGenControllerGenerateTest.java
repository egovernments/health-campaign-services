package org.egov.referralmanagement.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.producer.Producer;
import org.egov.referralmanagement.TestConfiguration;
import org.egov.referralmanagement.repository.DownsyncGenerationJobRepository;
import org.egov.referralmanagement.service.DownsyncFileGenService;
import org.egov.referralmanagement.service.DownsyncJobRegistry;
import org.egov.referralmanagement.service.JobHeartbeatScheduler;
import org.egov.referralmanagement.service.MasterDataService;
import org.egov.referralmanagement.web.models.DownsyncGenerationLocality;
import org.egov.referralmanagement.web.models.LocalityDownsyncCriteria;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /downsync/v1/_generate} — campaign-root resolution and PROJECT row layout.
 * <ol>
 *   <li>Any project id of the campaign is accepted and normalised to the root.</li>
 *   <li>PROJECT rows are one per village (same list as REGISTRY), projectId = root.</li>
 *   <li>The campaign beneficiaryType from MDMS is carried on every PROJECT criteria.</li>
 *   <li>Unknown project, childless root, or unresolvable beneficiaryType → 400, no job row.</li>
 * </ol>
 */
@WebMvcTest(DownsyncFileGenController.class)
@Import(TestConfiguration.class)
class DownsyncFileGenControllerGenerateTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private DownsyncFileGenService downsyncFileGenService;
    @MockBean private DownsyncGenerationJobRepository jobRepository;
    @MockBean private DownsyncJobRegistry jobRegistry;
    @MockBean private JobHeartbeatScheduler heartbeat;
    @MockBean private MasterDataService masterDataService;
    @MockBean private Producer producer;

    private static final String URL = "/downsync/v1/_generate";
    private static final String TENANT   = "ba";
    private static final String FACILITY = "be15b107-dd8d-4fb4-8ea3-7911a292781b";
    private static final String ROOT     = "b06c0d6c-4f54-4e77-9f28-c11e1f5f887e";
    private static final List<String> VILLAGES = List.of(
            "NIGERIAUAT_NI_02_01_10_01_15_KOGO", "NIGERIAUAT_NI_02_01_10_01_22_WARUM_NOMADI");

    private String body(String rootProjectId) {
        return "{\"RequestInfo\":{\"apiId\":\"t\",\"userInfo\":{\"uuid\":\"u-1\"}}," +
               "\"tenantId\":\"" + TENANT + "\"" +
               (rootProjectId == null ? "" : ",\"rootProjectId\":\"" + rootProjectId + "\"") + "}";
    }

    @BeforeEach
    void gatesOpen() {
        when(jobRegistry.isScanComplete()).thenReturn(true);
        when(jobRegistry.getActiveRegistryJobId(TENANT)).thenReturn(null);
        when(jobRegistry.getActiveProjectJobId(eq(TENANT), any())).thenReturn(null);
        when(jobRegistry.tryAcquireRegistry(eq(TENANT), any())).thenReturn(true);
        when(jobRegistry.tryAcquireProject(eq(TENANT), any(), any())).thenReturn(true);
        when(jobRepository.findInProgressJobByTenant(TENANT)).thenReturn(null);
        when(jobRepository.fetchAllLocalities(TENANT)).thenReturn(VILLAGES);
        when(jobRepository.fetchCampaignLocalities(eq(TENANT), any(), any())).thenReturn(VILLAGES);
        when(jobRepository.shouldRefreshMv(TENANT)).thenReturn(false);
        when(jobRepository.countOutcomes(eq(TENANT), any())).thenReturn(Map.of("succeeded", 4, "failed", 0));
    }

    @Test
    @DisplayName("202: facility id is normalised to the campaign root; PROJECT rows are per village with projectId=root and beneficiaryType")
    void generate_normalisesRootAndBuildsProjectRowsPerVillage() throws Exception {
        when(jobRepository.resolveRootProjectId(TENANT, FACILITY)).thenReturn(ROOT);
        when(jobRepository.countDescendantProjects(TENANT, ROOT)).thenReturn(205);
        when(jobRepository.findProjectTypeCode(TENANT, ROOT)).thenReturn("MR-DN");
        when(masterDataService.getBeneficiaryType(eq(TENANT), eq("MR-DN"), any())).thenReturn("INDIVIDUAL");

        mockMvc.perform(MockMvcRequestBuilders.post(URL).contentType(MediaType.APPLICATION_JSON).content(body(FACILITY)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // job row carries the ROOT, not the facility
        verify(jobRepository).insertJob(argThat(j -> ROOT.equals(j.getProjectId()) && j.getTotalRequested() == 4));

        ArgumentCaptor<DownsyncGenerationLocality> rows = ArgumentCaptor.forClass(DownsyncGenerationLocality.class);
        verify(jobRepository, times(4)).insertLocality(rows.capture());
        List<DownsyncGenerationLocality> project = rows.getAllValues().stream()
                .filter(r -> "PROJECT".equals(r.getCategory())).toList();
        assertEquals(2, project.size());
        assertTrue(project.stream().allMatch(r -> ROOT.equals(r.getProjectId())));
        assertEquals(VILLAGES.stream().sorted().toList(),
                project.stream().map(DownsyncGenerationLocality::getLocality).sorted().toList());

        // generation receives root + beneficiaryType on every PROJECT criteria (async, so wait)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LocalityDownsyncCriteria>> crit = ArgumentCaptor.forClass(List.class);
        verify(downsyncFileGenService, timeout(5000)).generateProject(crit.capture(), any());
        assertTrue(crit.getValue().stream().allMatch(c ->
                ROOT.equals(c.getProjectId()) && ROOT.equals(c.getRootProjectId()) && "INDIVIDUAL".equals(c.getBeneficiaryType())));
    }

    @Test
    @DisplayName("400 PROJECT_NOT_FOUND when the project id does not exist; no job inserted")
    void generate_unknownProject() throws Exception {
        when(jobRepository.resolveRootProjectId(TENANT, "ghost")).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.post(URL).contentType(MediaType.APPLICATION_JSON).content(body("ghost")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        verify(jobRepository, never()).insertJob(any());
    }

    @Test
    @DisplayName("202: a single-level campaign (root without children) is accepted, not rejected")
    void generate_rootWithoutChildren() throws Exception {
        when(jobRepository.resolveRootProjectId(TENANT, ROOT)).thenReturn(ROOT);
        when(jobRepository.countDescendantProjects(TENANT, ROOT)).thenReturn(0);
        when(jobRepository.findProjectTypeCode(TENANT, ROOT)).thenReturn("MR-DN");
        when(masterDataService.getBeneficiaryType(eq(TENANT), eq("MR-DN"), any())).thenReturn("INDIVIDUAL");
        mockMvc.perform(MockMvcRequestBuilders.post(URL).contentType(MediaType.APPLICATION_JSON).content(body(ROOT)))
                .andExpect(status().isAccepted());
        verify(jobRepository).insertJob(argThat(j -> ROOT.equals(j.getProjectId())));
    }

    @Test
    @DisplayName("PROJECT rows are created only for the campaign's own villages, not every village in the tenant")
    void generate_projectRowsScopedToCampaign() throws Exception {
        when(jobRepository.resolveRootProjectId(TENANT, ROOT)).thenReturn(ROOT);
        when(jobRepository.countDescendantProjects(TENANT, ROOT)).thenReturn(5);
        when(jobRepository.findProjectTypeCode(TENANT, ROOT)).thenReturn("Bednet");
        when(masterDataService.getBeneficiaryType(eq(TENANT), eq("Bednet"), any())).thenReturn("HOUSEHOLD");
        when(jobRepository.fetchCampaignLocalities(TENANT, ROOT, "HOUSEHOLD")).thenReturn(List.of(VILLAGES.get(0)));
        mockMvc.perform(MockMvcRequestBuilders.post(URL).contentType(MediaType.APPLICATION_JSON).content(body(ROOT)))
                .andExpect(status().isAccepted());
        verify(jobRepository).insertJob(argThat(j -> j.getTotalRequested() == 3));     // 2 registry + 1 project
        verify(jobRepository, times(1)).insertLocality(argThat(r -> "PROJECT".equals(r.getCategory()) && VILLAGES.get(0).equals(r.getLocality())));
    }

    @Test
    @DisplayName("503 BENEFICIARY_TYPE_LOOKUP_FAILED when MDMS is unreachable (transport error), with Retry-After")
    void generate_mdmsOutage() throws Exception {
        when(jobRepository.resolveRootProjectId(TENANT, ROOT)).thenReturn(ROOT);
        when(jobRepository.countDescendantProjects(TENANT, ROOT)).thenReturn(3);
        when(jobRepository.findProjectTypeCode(TENANT, ROOT)).thenReturn("MR-DN");
        when(masterDataService.getBeneficiaryType(eq(TENANT), eq("MR-DN"), any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("connect timed out"));
        mockMvc.perform(MockMvcRequestBuilders.post(URL).contentType(MediaType.APPLICATION_JSON).content(body(ROOT)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BENEFICIARY_TYPE_LOOKUP_FAILED"));
        verify(jobRepository, never()).insertJob(any());
    }

    @Test
    @DisplayName("409 while a job is running is returned before any MDMS call")
    void generate_conflictBeforeMdms() throws Exception {
        when(jobRepository.resolveRootProjectId(TENANT, ROOT)).thenReturn(ROOT);
        when(jobRegistry.getActiveRegistryJobId(TENANT)).thenReturn("running-job");
        when(jobRepository.findJobDetail(any(), any())).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.post(URL).contentType(MediaType.APPLICATION_JSON).content(body(ROOT)))
                .andExpect(status().isConflict());
        verify(masterDataService, never()).getBeneficiaryType(any(), any(), any());
    }

    @Test
    @DisplayName("400 BENEFICIARY_TYPE_UNRESOLVED when MDMS lookup fails; no silent fallback")
    void generate_beneficiaryTypeFailure() throws Exception {
        when(jobRepository.resolveRootProjectId(TENANT, ROOT)).thenReturn(ROOT);
        when(jobRepository.countDescendantProjects(TENANT, ROOT)).thenReturn(3);
        when(jobRepository.findProjectTypeCode(TENANT, ROOT)).thenReturn("MR-DN");
        when(masterDataService.getBeneficiaryType(eq(TENANT), eq("MR-DN"), any()))
                .thenThrow(new CustomException("BENEFICIARY_TYPE_MISSING", "no beneficiaryType"));
        mockMvc.perform(MockMvcRequestBuilders.post(URL).contentType(MediaType.APPLICATION_JSON).content(body(ROOT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BENEFICIARY_TYPE_UNRESOLVED"));
        verify(jobRepository, never()).insertJob(any());
    }

    @Test
    @DisplayName("registry-only job when no rootProjectId: no MDMS call, no PROJECT rows")
    void generate_registryOnly() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(URL).contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isAccepted());
        verify(masterDataService, never()).getBeneficiaryType(any(), any(), any());
        verify(jobRepository).insertJob(argThat(j -> j.getProjectId() == null && j.getTotalRequested() == 2));
        verify(jobRepository, times(2)).insertLocality(argThat(r -> "REGISTRY".equals(r.getCategory())));
    }
}
