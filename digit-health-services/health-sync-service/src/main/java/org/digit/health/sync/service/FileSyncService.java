package org.digit.health.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.digit.health.sync.context.enums.SyncErrorCode;
import org.digit.health.sync.orchestrator.client.SyncOrchestratorClient;
import org.digit.health.sync.orchestrator.client.metric.SyncLogMetric;
import org.digit.health.sync.repository.SyncLogRepository;
import org.digit.health.sync.service.checksum.ChecksumValidator;
import org.digit.health.sync.service.compressor.Compressor;
import org.digit.health.sync.web.models.AuditDetails;
import org.digit.health.sync.web.models.FileDetails;
import org.digit.health.sync.web.models.ReferenceId;
import org.digit.health.sync.web.models.SyncLogStatus;
import org.digit.health.sync.web.models.SyncUpDataList;
import org.digit.health.sync.web.models.dao.SyncLogData;
import org.digit.health.sync.web.models.request.SyncLogSearchDto;
import org.digit.health.sync.web.models.request.SyncLogSearchMapper;
import org.digit.health.sync.web.models.request.SyncUpDto;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service("fileSyncService")
public class FileSyncService implements SyncService {
    private final ObjectMapper objectMapper;
    private final FileStoreService fileStoreService;
    private final Compressor compressor;
    private final ChecksumValidator checksumValidator;
    private final SyncLogRepository syncLogRepository;
    private final SyncOrchestratorClient<Map<String, Object>, SyncLogMetric> orchestratorClient;

    @Autowired
    public FileSyncService(
            FileStoreService fileStoreService,
            ObjectMapper objectMapper,
            Compressor compressor,
            ChecksumValidator checksumValidator,
            @Qualifier("defaultSyncLogRepository") SyncLogRepository syncLogRepository,
            @Qualifier("healthCampaignSyncOrchestratorClient")
            SyncOrchestratorClient<Map<String, Object>, SyncLogMetric> orchestratorClient
            ) {
        this.fileStoreService = fileStoreService;
        this.objectMapper = objectMapper;
        this.compressor = compressor;
        this.checksumValidator = checksumValidator;
        this.syncLogRepository = syncLogRepository;
        this.orchestratorClient = orchestratorClient;
    }

    @Override
    @Async
    public void asyncSyncUp(SyncUpDto syncUpDto) {
        String tenantId = syncUpDto.getRequestInfo().getUserInfo().getTenantId();
        SyncLogData syncLogData = createSyncLog(syncUpDto);
        FileDetails fileDetails = syncUpDto.getFileDetails();
        byte[] data = fileStoreService.getFile(fileDetails.getFileStoreId(), tenantId);
        checksumValidator.validate(data, fileDetails.getChecksum());
        try {
            String str = convertToString(compressor.decompress(data));
            SyncUpDataList syncUpDataList = objectMapper.readValue(str, SyncUpDataList.class);
            log.info("Data de-serialized successfully");
            syncLogData.setTotalCount(syncUpDataList.getTotalCount());
            log.info("Persisting sync log with id {} status {}", syncLogData.getSyncId(),
                    syncLogData.getStatus());
            persistSyncLog(syncLogData);
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("syncUpDataList", syncUpDataList);
            payloadMap.put("syncId", syncLogData.getSyncId());
            payloadMap.put("tenantId", tenantId);
            payloadMap.put("requestInfo", syncUpDto.getRequestInfo());
            log.info("Starting orchestration");
            SyncLogMetric syncLogMetric = orchestratorClient.orchestrate(payloadMap);
            updateSyncLogData(syncLogData, syncLogMetric);
            log.info("Sync with id {} {}", syncLogData.getSyncId(), syncLogData.getStatus());
            syncLogRepository.update(syncLogData);
        } catch (Exception exception) {
            log.error("Exception occurred", exception);
            throw new CustomException(SyncErrorCode.ERROR_IN_SYNC.name(),
                    SyncErrorCode.ERROR_IN_SYNC.message(exception.getMessage()));
        }
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
                .syncId(syncUpDto.getSyncId())
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
