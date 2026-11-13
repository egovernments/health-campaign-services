package org.egov.workerregistry.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.workerregistry.config.WorkerRegistryConfiguration;
import org.egov.workerregistry.repository.IdGenRepository;
import org.egov.workerregistry.web.models.Worker;
import org.egov.workerregistry.web.models.WorkerBulkRequest;
import org.egov.workerregistry.web.models.WorkerIndividualMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@Slf4j
public class EnrichmentService {

    private final WorkerRegistryConfiguration config;
    private final IdGenRepository idGenRepository;

    @Autowired
    public EnrichmentService(WorkerRegistryConfiguration config, IdGenRepository idGenRepository) {
        this.config = config;
        this.idGenRepository = idGenRepository;
    }

    public void enrichCreate(List<Worker> workers, RequestInfo requestInfo) {
        long currentTime = System.currentTimeMillis();
        String createdBy = requestInfo.getUserInfo() != null ? requestInfo.getUserInfo().getUuid() : "system";

        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(createdBy)
                .createdTime(currentTime)
                .lastModifiedBy(createdBy)
                .lastModifiedTime(currentTime)
                .build();

        String tenantId = workers.get(0).getTenantId().split("\\.")[0];

        // Generate IDs only for workers without an ID
        long newCount = workers.stream().filter(w -> w.getId() == null || w.getId().isEmpty()).count();
        List<String> idList = null;
        if (newCount > 0) {
            idList = idGenRepository.getId(requestInfo, tenantId,
                    config.getIdgenName(), "", (int) newCount);
        }

        int idIndex = 0;
        for (Worker worker : workers) {
            if (worker.getId() == null || worker.getId().isEmpty()) {
                worker.setId(idList.get(idIndex++));
            }
            worker.setAuditDetails(auditDetails);
            worker.setRowVersion(1);
            if (worker.getIsDeleted() == null) {
                worker.setIsDeleted(false);
            }
        }
    }

    public void enrichCreate(WorkerBulkRequest request) {
        enrichCreate(request.getWorkers(), request.getRequestInfo());
    }

    public void enrichUpdate(List<Worker> workers, RequestInfo requestInfo) {
        long currentTime = System.currentTimeMillis();
        String modifiedBy = requestInfo.getUserInfo() != null ? requestInfo.getUserInfo().getUuid() : "system";

        for (Worker worker : workers) {
            if (worker.getAuditDetails() != null) {
                worker.getAuditDetails().setLastModifiedBy(modifiedBy);
                worker.getAuditDetails().setLastModifiedTime(currentTime);
            } else {
                worker.setAuditDetails(AuditDetails.builder()
                        .createdBy(modifiedBy)
                        .createdTime(currentTime)
                        .lastModifiedBy(modifiedBy)
                        .lastModifiedTime(currentTime)
                        .build());
            }
            worker.setRowVersion(worker.getRowVersion() != null ? worker.getRowVersion() + 1 : 2);
        }
    }

    public void enrichMapIndividual(List<WorkerIndividualMap> maps, RequestInfo requestInfo) {
        long currentTime = System.currentTimeMillis();
        String createdBy = requestInfo.getUserInfo() != null ? requestInfo.getUserInfo().getUuid() : "system";

        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(createdBy)
                .createdTime(currentTime)
                .lastModifiedBy(createdBy)
                .lastModifiedTime(currentTime)
                .build();

        for (WorkerIndividualMap map : maps) {
            if (map.getId() == null || map.getId().isEmpty()) {
                map.setId(UUID.randomUUID().toString());
            }
            if (map.getIsDeleted() == null) {
                map.setIsDeleted(false);
            }
            map.setAuditDetails(auditDetails);
        }
    }
}
