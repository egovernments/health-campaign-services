package org.digit.health.sync.service;

import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.web.models.AuditDetails;
import org.digit.health.sync.web.models.FileDetails;
import org.digit.health.sync.web.models.ReferenceId;
import org.digit.health.sync.web.models.SyncErrorDetailsLog;
import org.digit.health.sync.web.models.SyncLog;
import org.digit.health.sync.web.models.SyncStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SyncLogService {

    private final Producer producer;

    @Autowired
    public SyncLogService(Producer producer) {
        this.producer = producer;
    }

    public void persistSyncLog() {
        SyncLog syncLog = SyncLog.builder().syncId("test-id")
                .referenceId(ReferenceId.builder()
                        .id("campaign-id")
                        .type("campaign")
                        .build())
                .tenantId("tenant-id")
                .fileDetails(FileDetails.builder()
                        .fileStoreId("fileStore-id")
                        .checksum("checksum")
                        .build())
                .status(SyncStatus.PARTIALLY_COMPLETED)
                .comment("comment")
                .totalCount(10)
                .successCount(5)
                .errorCount(5)
                .auditDetails(AuditDetails.builder()
                        .createdBy("some-user")
                        .createdTime(1234567L)
                        .lastModifiedTime(12345678L)
                        .lastModifiedBy("some-user")
                        .build())
                .build();
        producer.send("health-sync-log", syncLog);
    }

    public void persistSyncErrorDetailsLog() {
        SyncErrorDetailsLog syncErrorDetailsLog = SyncErrorDetailsLog.builder()
                .syncErrorDetailsId("detail-id")
                .syncId("sync-id")
                .tenantId("tenant-id")
                .recordId("record-id")
                .recordIdType("record-id-type")
                .errorCodes("some-codes")
                .errorMessages("some-messages")
                .auditDetails(AuditDetails.builder()
                        .createdBy("some-user")
                        .createdTime(1234567L)
                        .lastModifiedTime(12345678L)
                        .lastModifiedBy("some-user")
                        .build())
                .build();
        producer.send("health-sync-error-details-log", syncErrorDetailsLog);
    }
}
