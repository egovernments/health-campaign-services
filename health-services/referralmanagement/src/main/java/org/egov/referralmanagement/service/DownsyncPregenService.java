package org.egov.referralmanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.DownsyncGenerationJobRepository;
import org.egov.referralmanagement.web.models.DownsyncFileLink;
import org.egov.referralmanagement.web.models.DownsyncLocalityFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DownsyncPregenService {

    @Autowired private DownsyncGenerationJobRepository jobRepository;
    @Autowired private DownsyncS3Service s3Service;
    @Autowired private ReferralManagementConfiguration config;

    /**
     * Returns presigned download URLs for the latest pre-generated files.
     *
     * Step 1 — Registry files (HH_MEMBERS + INDIVIDUALS): latest SUCCESS locality row
     *           where projectId IS NULL.
     * Step 2 — Project files (BENE_AE_REF + TASKS): resolve leaf projectId from rootProjectId,
     *           then fetch its SUCCESS locality row.
     *
     * Returns empty list if nothing found — caller falls through to live scan.
     */
    public List<DownsyncFileLink> getPregenLinks(DownsyncCriteria criteria) {
        String tenantId      = criteria.getTenantId();
        String locality      = criteria.getLocality();
        String rootProjectId = criteria.getProjectId(); // treated as rootProjectId

        long expiresAt = System.currentTimeMillis()
                + (long) config.getPresignedUrlExpirySecs() * 1000;

        List<DownsyncFileLink> links = new ArrayList<>();

        // Step 1 — Registry files (projectId = null)
        for (DownsyncLocalityFile f : jobRepository.findLatestFilesForLocality(tenantId, null, locality))
            links.add(toLink(f, expiresAt));

        // Step 2 — Project files (resolved leaf projectId)
        if (StringUtils.hasText(rootProjectId)) {
            String leafProjectId = jobRepository.findLeafProjectIdForLocality(tenantId, rootProjectId, locality);
            if (leafProjectId != null) {
                for (DownsyncLocalityFile f : jobRepository.findLatestFilesForLocality(tenantId, leafProjectId, locality))
                    links.add(toLink(f, expiresAt));
            } else {
                log.debug("No leaf project found for rootProjectId={} locality={} tenant={}",
                        rootProjectId, locality, tenantId);
            }
        }

        if (links.isEmpty())
            log.debug("No pre-generated files found for locality={} tenant={} rootProject={}",
                    locality, tenantId, rootProjectId);

        return links;
    }

    private DownsyncFileLink toLink(DownsyncLocalityFile f, long expiresAt) {
        return DownsyncFileLink.builder()
                .fileType(f.getFileType())
                .url(s3Service.presign(f.getS3Key()))
                .recordCount(f.getRecordCount())
                .expiresAt(expiresAt)
                .build();
    }
}
