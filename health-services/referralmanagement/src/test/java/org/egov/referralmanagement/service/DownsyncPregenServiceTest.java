package org.egov.referralmanagement.service;

import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.DownsyncGenerationJobRepository;
import org.egov.referralmanagement.web.models.DownsyncFileLink;
import org.egov.referralmanagement.web.models.DownsyncLocalityFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The app sends the worker's own projectId (facility, ward, village…). PROJECT files are
 * stored per campaign root, so the lookup must resolve the root first and never try to
 * find a "leaf" project by boundary.
 */
@ExtendWith(MockitoExtension.class)
class DownsyncPregenServiceTest {

    @Mock DownsyncGenerationJobRepository repo;
    @Mock DownsyncS3Service s3;
    @Mock ReferralManagementConfiguration config;
    @InjectMocks DownsyncPregenService service;

    private static final String TENANT = "ba", LOC = "NIGERIAUAT_NI_02_01_10_01_15_KOGO";
    private static final String FACILITY = "be15b107-dd8d-4fb4-8ea3-7911a292781b";
    private static final String ROOT     = "b06c0d6c-4f54-4e77-9f28-c11e1f5f887e";

    private DownsyncCriteria criteria(String projectId) {
        DownsyncCriteria c = new DownsyncCriteria();
        c.setTenantId(TENANT); c.setLocality(LOC); c.setProjectId(projectId);
        return c;
    }

    private static DownsyncLocalityFile file(String type, String key) {
        return DownsyncLocalityFile.builder().fileType(type).s3Key(key).recordCount(1L).build();
    }

    @Test
    @DisplayName("facility-level worker gets registry files + PROJECT files of the campaign root")
    void resolvesRootFromWorkerProject() {
        when(repo.resolveRootProjectId(TENANT, FACILITY)).thenReturn(ROOT);
        when(repo.findLatestFilesForLocality(TENANT, null, LOC))
                .thenReturn(List.of(file("HH_MEMBERS", "ba/" + LOC + "/hh_members.ndjson.gz")));
        when(repo.findLatestFilesForLocality(TENANT, ROOT, LOC))
                .thenReturn(List.of(file("BENE_AE_REF", "ba/" + ROOT + "/" + LOC + "/bene_ae_ref.ndjson.gz")));

        List<DownsyncFileLink> links = service.getPregenLinks(criteria(FACILITY));

        assertEquals(2, links.size());
        assertTrue(links.stream().anyMatch(l -> "BENE_AE_REF".equals(l.getFileType())));
        verify(repo).findLatestFilesForLocality(TENANT, ROOT, LOC);
        verify(repo, never()).findLatestFilesForLocality(TENANT, FACILITY, LOC);
    }

    @Test
    @DisplayName("unknown projectId yields registry files only")
    void unknownProject() {
        when(repo.resolveRootProjectId(TENANT, "nope")).thenReturn(null);
        when(repo.findLatestFilesForLocality(TENANT, null, LOC)).thenReturn(List.of(file("INDIVIDUALS", "k")));

        List<DownsyncFileLink> links = service.getPregenLinks(criteria("nope"));

        assertEquals(1, links.size());
        verify(repo, times(1)).findLatestFilesForLocality(eq(TENANT), any(), eq(LOC));
    }

    @Test
    @DisplayName("no projectId → no root resolution attempted")
    void noProject() {
        when(repo.findLatestFilesForLocality(TENANT, null, LOC)).thenReturn(List.of());
        assertTrue(service.getPregenLinks(criteria(null)).isEmpty());
        verify(repo, never()).resolveRootProjectId(any(), any());
    }
}
