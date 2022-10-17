package org.digit.health.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.digit.health.sync.context.enums.SyncErrorCode;
import org.digit.health.sync.kafka.Producer;
import org.digit.health.sync.orchestrator.client.SyncOrchestratorClient;
import org.digit.health.sync.web.models.SyncLogStatus;
import org.digit.health.sync.orchestrator.client.metric.SyncLogMetric;
import org.digit.health.sync.repository.SyncLogRepository;
import org.digit.health.sync.service.checksum.ChecksumValidator;
import org.digit.health.sync.service.checksum.Md5ChecksumValidator;
import org.digit.health.sync.service.compressor.Compressor;
import org.digit.health.sync.service.compressor.GzipCompressor;
import org.digit.health.sync.web.models.AuditDetails;
import org.digit.health.sync.web.models.FileDetails;
import org.digit.health.sync.web.models.ReferenceId;
import org.digit.health.sync.web.models.SyncErrorDetailsLog;
import org.digit.health.sync.web.models.SyncId;
import org.digit.health.sync.web.models.SyncUpDataList;
import org.digit.health.sync.web.models.dao.SyncLogData;
import org.digit.health.sync.web.models.request.SyncLogSearchDto;
import org.digit.health.sync.web.models.request.SyncLogSearchMapper;
import org.digit.health.sync.web.models.request.SyncUpDto;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service("fileSyncService")
public class FileSyncService implements SyncService {

    private final Producer producer;
    private final ObjectMapper objectMapper;
    private final FileStoreService fileStoreService;
    private final Compressor compressor;
    private final ChecksumValidator checksumValidator;
    private final SyncLogRepository syncLogRepository;
    private final SyncOrchestratorClient<Map<String, Object>, SyncLogMetric> orchestratorClient;

    @Autowired
    public FileSyncService(
            Producer producer,
            FileStoreService fileStoreService,
            ObjectMapper objectMapper,
            GzipCompressor compressor,
            Md5ChecksumValidator checksumValidator,
            @Qualifier("defaultSyncLogRepository") SyncLogRepository syncLogRepository,
            @Qualifier("healthCampaignSyncOrchestratorClient")
            SyncOrchestratorClient<Map<String, Object>, SyncLogMetric> orchestratorClient
            ) {
        this.producer = producer;
        this.fileStoreService = fileStoreService;
        this.objectMapper = objectMapper;
        this.compressor = compressor;
        this.checksumValidator = checksumValidator;
        this.syncLogRepository = syncLogRepository;
        this.orchestratorClient = orchestratorClient;
    }

    @Override
    public SyncId syncUp(SyncUpDto syncUpDto) {
        String tenantId = syncUpDto.getRequestInfo().getUserInfo().getTenantId();
        SyncLogData syncLogData = createSyncLog(syncUpDto);
        FileDetails fileDetails = syncUpDto.getFileDetails();
        byte[] data = fileStoreService.getFile(fileDetails.getFileStoreId(), tenantId);
        checksumValidator.validate(data, fileDetails.getChecksum());
        try {
            String str = convertToString(compressor.decompress(data));
            SyncUpDataList syncUpDataList = objectMapper.readValue(str, SyncUpDataList.class);
            syncLogData.setTotalCount(syncUpDataList.getTotalCount());
            persistSyncLog(syncLogData);
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("syncUpDataList", syncUpDataList);
            payloadMap.put("syncId", syncLogData.getSyncId());
            payloadMap.put("tenantId", tenantId);
            payloadMap.put("requestInfo", syncUpDto.getRequestInfo());
            SyncLogMetric syncLogMetric = orchestratorClient.orchestrate(payloadMap);
            updateSyncLogData(syncLogData, syncLogMetric);
            syncLogRepository.update(syncLogData);
        } catch (Exception exception) {
            log.error("Exception occurred: ", exception);
            throw new CustomException(SyncErrorCode.ERROR_IN_MAPPING_JSON.name(),
                    SyncErrorCode.ERROR_IN_MAPPING_JSON.message());
        }
        return SyncId.builder().syncId(syncLogData.getSyncId()).build();

    }

    private void updateSyncLogData(SyncLogData syncLogData, SyncLogMetric syncLogMetric) {
        syncLogData.setErrorCount(syncLogMetric.getErrorCount());
        syncLogData.setSuccessCount(syncLogMetric.getSuccessCount());
        syncLogData.setStatus(syncLogMetric.getSyncLogStatus());
    }

    private String convertToString(byte[] data) {
        try {
            return IOUtils.toString(data, StandardCharsets.UTF_8.toString());
        } catch (IOException exception) {
            log.error("Could not decompress file", exception);
            throw new CustomException(SyncErrorCode.ERROR_IN_DECOMPRESSION.name(),
                    SyncErrorCode.ERROR_IN_DECOMPRESSION.message());
        }
    }

    private SyncLogData createSyncLog(SyncUpDto syncUpDto) {
        User userInfo = syncUpDto.getRequestInfo().getUserInfo();
        long createdTime = System.currentTimeMillis();
        FileDetails fileDetails = syncUpDto.getFileDetails();

        return SyncLogData.builder()
                .syncId(UUID.randomUUID().toString())
                .status(SyncLogStatus.CREATED)
                .referenceId(ReferenceId.builder()
                        .id(syncUpDto.getReferenceId().getId())
                        .type(syncUpDto.getReferenceId().getType())
                        .build())
                .tenantId(userInfo.getTenantId())
                .errorCount(0L)
                .successCount(0L)
                .totalCount(0L)
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

    private void persistSyncLog(SyncLogData syncLogData) {
        syncLogRepository.save(syncLogData);
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

    @Override
    public List<SyncLogData> find(SyncLogSearchDto syncLogSearchDto) {
        return syncLogRepository.find(
                SyncLogSearchMapper.INSTANCE
                        .toData(
                                syncLogSearchDto
                        )
        );
    }

}
