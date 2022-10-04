package org.digit.health.sync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.service.checksum.Checksum;
import org.digit.health.sync.service.checksum.MD5Checksum;
import org.digit.health.sync.service.compressor.Compressor;
import org.digit.health.sync.service.compressor.GzipCompressor;
import org.digit.health.sync.web.models.AuditDetails;
import org.digit.health.sync.web.models.FileDetails;
import org.digit.health.sync.web.models.ReferenceId;
import org.digit.health.sync.web.models.SyncErrorDetailsLog;
import org.digit.health.sync.web.models.SyncLog;
import org.digit.health.sync.web.models.SyncStatus;
import org.digit.health.sync.web.models.request.SyncUpDto;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.UUID;

@Slf4j
@Service
public class FileSyncService implements SyncService {

    private final Producer producer;
    private final ObjectMapper objectMapper;
    private final FileStoreService fileStoreService;
    private final Compressor compressor;
    private final Checksum checksum;


    @Autowired
    public FileSyncService(Producer producer, FileStoreService fileStoreService, ObjectMapper objectMapper, GzipCompressor compressor, MD5Checksum checksumValidator) {
        this.producer = producer;
        this.fileStoreService = fileStoreService;
        this.objectMapper = objectMapper;
        this.compressor = compressor;
        this.checksum = checksumValidator;
    }

    @Override
    public String sync(SyncUpDto syncUpDto) {
        String tenantId = syncUpDto.getRequestInfo().getUserInfo().getTenantId();
        SyncLog syncLog = initiateSyncLog(syncUpDto);
        FileDetails fileDetails = syncUpDto.getFileDetails();
        try {
            byte[] data = fileStoreService.getFile(fileDetails.getFileStoreId(), tenantId);
            checksum.validate(data, fileDetails.getChecksum());
            HashMap json = objectMapper.readValue(read(data), HashMap.class);
            log.info(json.entrySet().toString());
            persistSyncLog(syncLog);
        } catch (NoSuchAlgorithmException ex) {
            handleSyncError(syncLog, ex, "NO_ALGORITHM_FOUND");
        } catch (JsonProcessingException ex) {
            handleSyncError(syncLog, ex, "Invalid JSON");
        } catch (HttpServerErrorException | IOException ex) {
            handleSyncError(syncLog, ex, "Invalid File");
        } catch (Exception ex) {
            handleSyncError(syncLog, ex, "Unable to process");
        }
        return syncLog.getSyncId();

    }


    private String read(byte[] data) throws IOException {
        BufferedReader br = compressor.decompress(new ByteArrayInputStream(data));
        String message = org.apache.commons.io.IOUtils.toString(br);
        return message;
    }

    private SyncLog initiateSyncLog(SyncUpDto syncUpDto) {
        User userInfo = syncUpDto.getRequestInfo().getUserInfo();
        long createdTime = System.currentTimeMillis();
        FileDetails fileDetails = syncUpDto.getFileDetails();

        return SyncLog.builder()
                .syncId(UUID.randomUUID().toString())
                .status(SyncStatus.CREATED)
                .referenceId(ReferenceId.builder()
                        .id(syncUpDto.getReferenceId().getId())
                        .type(syncUpDto.getReferenceId().getType())
                        .build())
                .tenantId(userInfo.getTenantId())
                .auditDetails(AuditDetails.builder()
                        .createdBy(userInfo.getUuid())
                        .createdTime(createdTime)
                        .lastModifiedTime(createdTime)
                        .lastModifiedBy(userInfo.getUuid())
                        .build())
                .fileDetails(FileDetails.builder()
                        .fileStoreId(fileDetails.getFileStoreId())
                        .checksum(fileDetails.getChecksum())
                        .build())
                .build();
    }

    private void handleSyncError(SyncLog syncLog, Exception ex, String errorCode) {
        log.error(ex.getMessage());
        syncLog.setComment(ex.getMessage());
        syncLog.setStatus(SyncStatus.FAILED);
        persistSyncLog(syncLog);
        throw new CustomException(errorCode, ex.getMessage());
    }

    private void persistSyncLog(SyncLog syncLog) {
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
