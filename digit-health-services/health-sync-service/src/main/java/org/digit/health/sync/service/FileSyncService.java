package org.digit.health.sync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.digit.health.sync.context.SyncErrorCode;
import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.service.checksum.ChecksumValidator;
import org.digit.health.sync.service.checksum.Md5ChecksumValidator;
import org.digit.health.sync.service.compressor.Compressor;
import org.digit.health.sync.service.compressor.GzipCompressor;
import org.digit.health.sync.web.models.*;
import org.digit.health.sync.web.models.request.SyncUpDto;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

@Slf4j
@Service
public class FileSyncService implements SyncService {

    private final Producer producer;
    private final ObjectMapper objectMapper;
    private final FileStoreService fileStoreService;
    private final Compressor compressor;
    private final ChecksumValidator checksumValidator;


    @Autowired
    public FileSyncService(Producer producer, FileStoreService fileStoreService, ObjectMapper objectMapper, GzipCompressor compressor, Md5ChecksumValidator checksumValidator) {
        this.producer = producer;
        this.fileStoreService = fileStoreService;
        this.objectMapper = objectMapper;
        this.compressor = compressor;
        this.checksumValidator = checksumValidator;
    }

    @Override
    public SyncId syncUp(SyncUpDto syncUpDto) {
        String tenantId = syncUpDto.getRequestInfo().getUserInfo().getTenantId();
        SyncLog syncLog = createSyncLog(syncUpDto);
        FileDetails fileDetails = syncUpDto.getFileDetails();
        try {
            byte[] data = fileStoreService.getFile(fileDetails.getFileStoreId(), tenantId);
            checksumValidator.validate(data, fileDetails.getChecksum());
            HashMap json = objectMapper.readValue(read(data), HashMap.class);
            log.info(json.toString());
            persistSyncLog(syncLog);
        } catch (JsonProcessingException ex) {
            handleSyncError(syncLog, ex , SyncErrorCode.INVALID_JSON_FILE);
        } catch (HttpServerErrorException | IOException ex) {
            handleSyncError(syncLog, ex, SyncErrorCode.INVALID_FILE);
        } catch (Exception ex) {
            handleSyncError(syncLog, ex, SyncErrorCode.UNABLE_TO_PROCESS);
        }
        return SyncId.builder().syncId(syncLog.getSyncId()).build();

    }


    private String read(byte[] data) throws IOException {
        return org.apache.commons.io.IOUtils.toString(compressor.decompress(data));
    }

    private SyncLog createSyncLog(SyncUpDto syncUpDto) {
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

    private void handleSyncError(SyncLog syncLog, Exception ex, SyncErrorCode errorCode) {
        log.error(ex.getMessage());
        syncLog.setComment(errorCode.message(ex.getMessage()));
        syncLog.setStatus(SyncStatus.FAILED);
        persistSyncLog(syncLog);
        throw new CustomException(errorCode.name(), errorCode.message(ex.getMessage()));
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
