package org.egov.workerregistry.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.ErrorDetails;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.tracer.model.CustomException;
import org.egov.workerregistry.repository.WorkerIndividualMapRepository;
import org.egov.workerregistry.repository.WorkerRepository;
import org.egov.workerregistry.constants.WorkerRegistryConstants;
import org.egov.workerregistry.validators.*;
import org.egov.workerregistry.web.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WorkerService {

    private final EnrichmentService enrichmentService;
    private final WorkerRepository workerRepository;
    private final WorkerIndividualMapRepository workerIndividualMapRepository;
    private final WorkerEncryptionService workerEncryptionService;
    private final List<Validator<WorkerBulkRequest, Worker>> validators;
    private final List<Validator<WorkerIndividualMapBulkRequest, WorkerIndividualMap>> individualMapValidators;

    private final Predicate<Validator<WorkerBulkRequest, Worker>> isApplicableForCreate = v ->
            v.getClass().equals(WNonEmptyWorkerListValidator.class)
                    || v.getClass().equals(WTenantIdValidator.class)
                    || v.getClass().equals(WIndividualIdValidator.class);

    private final Predicate<Validator<WorkerBulkRequest, Worker>> isApplicableForUpdate = v ->
            v.getClass().equals(WNullIdValidator.class)
                    || v.getClass().equals(WTenantIdValidator.class)
                    || v.getClass().equals(WIndividualIdValidator.class)
                    || v.getClass().equals(WNonExistentEntityValidator.class)
                    || v.getClass().equals(WRowVersionValidator.class);

    private final Predicate<Validator<WorkerIndividualMapBulkRequest, WorkerIndividualMap>> isApplicableForIndividualMap =
            v -> v.getClass().equals(WIndividualIdUniqueValidator.class);

    @Autowired
    public WorkerService(EnrichmentService enrichmentService,
                         WorkerRepository workerRepository,
                         WorkerIndividualMapRepository workerIndividualMapRepository,
                         WorkerEncryptionService workerEncryptionService,
                         List<Validator<WorkerBulkRequest, Worker>> validators,
                         List<Validator<WorkerIndividualMapBulkRequest, WorkerIndividualMap>> individualMapValidators) {
        this.enrichmentService = enrichmentService;
        this.workerRepository = workerRepository;
        this.workerIndividualMapRepository = workerIndividualMapRepository;
        this.workerEncryptionService = workerEncryptionService;
        this.validators = validators;
        this.individualMapValidators = individualMapValidators;
    }

    public List<Worker> create(WorkerBulkRequest request) {
        Tuple<List<Worker>, Map<Worker, ErrorDetails>> validationResult = CommonUtils.validate(
                validators, isApplicableForCreate, request,
                "setWorkers", "getWorkers", WorkerRegistryConstants.VALIDATION_ERROR, false);

        List<Worker> validWorkers = validationResult.getX();
        Map<Worker, ErrorDetails> errorDetailsMap = validationResult.getY();

        if(!errorDetailsMap.isEmpty()) {
            CommonUtils.handleErrors(errorDetailsMap, false, WorkerRegistryConstants.VALIDATION_ERROR);
        }

        if (CollectionUtils.isEmpty(validWorkers)) {
            return new ArrayList<>();
        }

        String tenantId = validWorkers.get(0).getTenantId();

        enrichmentService.enrichCreate(validWorkers, request.getRequestInfo());
        List<WorkerIndividualMap> validMaps = new ArrayList<>();
        validWorkers.forEach(validWorker -> {
            if(!CollectionUtils.isEmpty(validWorker.getIndividualIds())) {
                validWorker.getIndividualIds().forEach(individualId -> {
                    validMaps.add(WorkerIndividualMap.builder()
                            .tenantId(tenantId)
                            .workerId(validWorker.getId())
                            .individualId(individualId)
                            .isDeleted(false)
                            .build()
                    );
                });
            }
        });

        List<Worker> encryptedWorkers = workerEncryptionService.encrypt(validWorkers, WorkerRegistryConstants.ENCRYPT_WORKER);
        workerRepository.putInCache(encryptedWorkers);
        workerRepository.save(encryptedWorkers, tenantId);

        if(!CollectionUtils.isEmpty(validMaps)) {
            enrichmentService.enrichMapIndividual(validMaps, request.getRequestInfo());
            workerIndividualMapRepository.putInCache(validMaps);
            workerIndividualMapRepository.save(validMaps, tenantId);
        }

        CommonUtils.handleErrors(errorDetailsMap, false, WorkerRegistryConstants.VALIDATION_ERROR);
        return encryptedWorkers;
    }

    public List<Worker> update(WorkerBulkRequest request) {
        Tuple<List<Worker>, Map<Worker, ErrorDetails>> validationResult = CommonUtils.validate(
                validators, isApplicableForUpdate, request,
                "setWorkers", "getWorkers", WorkerRegistryConstants.VALIDATION_ERROR, false);

        List<Worker> validWorkers = validationResult.getX();
        Map<Worker, ErrorDetails> errorDetailsMap = validationResult.getY();

        if (CollectionUtils.isEmpty(validWorkers)) {
            CommonUtils.handleErrors(errorDetailsMap, false, WorkerRegistryConstants.VALIDATION_ERROR);
            return new ArrayList<>();
        }

        String tenantId = validWorkers.get(0).getTenantId();

        // Merge with existing DB records
        List<String> ids = validWorkers.stream().map(Worker::getId).collect(Collectors.toList());
        Map<String, Worker> existingMap = new HashMap<>();
        try {
            WorkerSearch search = WorkerSearch.builder().id(ids).tenantId(tenantId).build();
            List<Worker> existing = workerRepository.find(search);
            existing.forEach(w -> existingMap.put(w.getId(), w));
        } catch (InvalidTenantIdException e) {
            throw new CustomException(WorkerRegistryConstants.INVALID_TENANT_EXCEPTION, WorkerRegistryConstants.MSG_TENANT_ID_NOT_VALID);
        }
        validWorkers.forEach(w -> {
            Worker existing = existingMap.get(w.getId());
            if (existing != null) {
                mergeWorker(w, existing);
            }
        });

        enrichmentService.enrichUpdate(validWorkers, request.getRequestInfo());
        List<Worker> encryptedWorkers = workerEncryptionService.encrypt(validWorkers, WorkerRegistryConstants.ENCRYPT_WORKER);
        workerRepository.putInCache(encryptedWorkers);
        workerRepository.update(encryptedWorkers, tenantId);

        CommonUtils.handleErrors(errorDetailsMap, false, WorkerRegistryConstants.VALIDATION_ERROR);
        return encryptedWorkers;
    }

    public List<WorkerIndividualMap> mapIndividual(WorkerIndividualMapBulkRequest request) {
        Tuple<List<WorkerIndividualMap>, Map<WorkerIndividualMap, ErrorDetails>> validationResult = CommonUtils.validate(
                individualMapValidators, isApplicableForIndividualMap, request,
                "setWorkerIndividualMaps", "getWorkerIndividualMaps", WorkerRegistryConstants.VALIDATION_ERROR, false);

        List<WorkerIndividualMap> validMaps = validationResult.getX();
        Map<WorkerIndividualMap, ErrorDetails> errorDetailsMap = validationResult.getY();

        if (CollectionUtils.isEmpty(validMaps)) {
            CommonUtils.handleErrors(errorDetailsMap, false, WorkerRegistryConstants.VALIDATION_ERROR);
            return new ArrayList<>();
        }

        String tenantId = validMaps.get(0).getTenantId();
        enrichmentService.enrichMapIndividual(validMaps, request.getRequestInfo());
        workerIndividualMapRepository.save(validMaps, tenantId);

        CommonUtils.handleErrors(errorDetailsMap, false, WorkerRegistryConstants.VALIDATION_ERROR);
        return validMaps;
    }

    public List<Worker> search(WorkerSearchRequest request) {
        WorkerSearch searchCriteria = request.getWorkerSearch();
        if (searchCriteria == null || searchCriteria.getTenantId() == null) {
            throw new CustomException(WorkerRegistryConstants.INVALID_REQUEST, WorkerRegistryConstants.MSG_TENANT_ID_REQUIRED);
        }

        WorkerSearch encryptedSearch = workerEncryptionService.encrypt(searchCriteria, WorkerRegistryConstants.ENCRYPT_WORKER_SEARCH);
        request.setWorkerSearch(encryptedSearch);
        Map<String, List<String>> workerIdIndividualIdsMap = new HashMap<>();
        String tenantId = searchCriteria.getTenantId();
        List<String> individualIds = searchCriteria.getIndividualId();

        if (!CollectionUtils.isEmpty(request.getWorkerSearch().getIndividualId())) {
            List<String> workerIds;
            try {
                List<WorkerIndividualMap> workerIndividualMaps = workerIndividualMapRepository
                        .find(request.getWorkerSearch().getIndividualId(), request.getWorkerSearch().getTenantId());
                workerIdIndividualIdsMap = workerIndividualMaps.stream()
                        .collect(Collectors.groupingBy(
                                WorkerIndividualMap::getWorkerId,
                                Collectors.mapping(
                                        WorkerIndividualMap::getIndividualId,
                                        Collectors.toList()
                                )
                        ));
                workerIds = workerIdIndividualIdsMap.keySet().stream().toList();
            } catch (InvalidTenantIdException e) {
                throw new CustomException(WorkerRegistryConstants.INVALID_TENANT_EXCEPTION, WorkerRegistryConstants.MSG_TENANT_ID_NOT_VALID);
            }
            if (CollectionUtils.isEmpty(request.getWorkerSearch().getId())) {
                request.getWorkerSearch().setId(workerIds);
            }
        }

        List<Worker> workers;
        try {
            workers = workerRepository.find(request.getWorkerSearch());
        } catch (InvalidTenantIdException e) {
            throw new CustomException("INVALID_TENANT_EXCEPTION", "The tenant id is not valid");
        }

        if (CollectionUtils.isEmpty(individualIds)) {
            try {
                List<WorkerIndividualMap> workerIndividualMaps = workerIndividualMapRepository
                        .findByWorkerIds(searchCriteria.getId(), request.getWorkerSearch().getTenantId());
                workerIdIndividualIdsMap = workerIndividualMaps.stream()
                        .collect(Collectors.groupingBy(
                                WorkerIndividualMap::getWorkerId,
                                Collectors.mapping(
                                        WorkerIndividualMap::getIndividualId,
                                        Collectors.toList()
                                )
                        ));
            } catch (InvalidTenantIdException e) {
                throw new CustomException(WorkerRegistryConstants.INVALID_TENANT_EXCEPTION, WorkerRegistryConstants.MSG_TENANT_ID_NOT_VALID);
            }
        }

        Map<String, List<String>> finalWorkerIdIndividualIdsMap = workerIdIndividualIdsMap;
        workers.forEach(worker -> {
            List<String> indIds = finalWorkerIdIndividualIdsMap.getOrDefault(worker.getId(), new ArrayList<>());
            worker.setIndividualIds(indIds);
        });

        return workerEncryptionService.decrypt(workers, WorkerRegistryConstants.DECRYPT_WORKER, request.getRequestInfo());
    }

    private void mergeWorker(Worker incoming, Worker existing) {
        if (incoming.getName() == null) incoming.setName(existing.getName());
        if (incoming.getPayeePhoneNumber() == null) incoming.setPayeePhoneNumber(existing.getPayeePhoneNumber());
        if (incoming.getPaymentProvider() == null) incoming.setPaymentProvider(existing.getPaymentProvider());
        if (incoming.getPayeeName() == null) incoming.setPayeeName(existing.getPayeeName());
        if (incoming.getBankAccount() == null) incoming.setBankAccount(existing.getBankAccount());
        if (incoming.getBankCode() == null) incoming.setBankCode(existing.getBankCode());
        if (incoming.getPhotoId() == null) incoming.setPhotoId(existing.getPhotoId());
        if (incoming.getSignatureId() == null) incoming.setSignatureId(existing.getSignatureId());
        if (incoming.getAdditionalDetails() == null) incoming.setAdditionalDetails(existing.getAdditionalDetails());
        incoming.setAuditDetails(existing.getAuditDetails());
        incoming.setRowVersion(existing.getRowVersion());
    }

    public void processAttendanceDocumentEvent(AttendanceDocumentEvent event, RequestInfo requestInfo) {
        if (event == null || event.getIndividualId() == null || event.getTenantId() == null
                || event.getFileStore() == null || event.getType() == null) {
            log.warn("Skipping invalid AttendanceDocumentEvent: {}", event);
            return;
        }

        String tenantId = event.getTenantId();
        List<String> individualIds = List.of(event.getIndividualId());

        try {
            List<String> workerIds = workerIndividualMapRepository.findWorkerIdsByIndividualIds(individualIds, tenantId);
            if (workerIds.isEmpty()) {
                log.warn("No workers found for individualId: {}", event.getIndividualId());
                return;
            }

            String workerId = workerIds.get(0);
            WorkerSearch search = WorkerSearch.builder().id(List.of(workerId)).tenantId(tenantId).build();
            List<Worker> workers = workerRepository.find(search);
            if (workers.isEmpty()) {
                log.warn("Worker not found for workerId: {}", workerId);
                return;
            }

            Worker worker = workers.get(0);
            boolean needsUpdate = false;

            if (WorkerRegistryConstants.DOCUMENT_TYPE_SIGNATURE.equalsIgnoreCase(event.getType())
                    && (worker.getSignatureId() == null || worker.getSignatureId().isEmpty())) {
                worker.setSignatureId(event.getFileStore());
                needsUpdate = true;
            } else if (WorkerRegistryConstants.DOCUMENT_TYPE_PHOTO.equalsIgnoreCase(event.getType())
                    && (worker.getPhotoId() == null || worker.getPhotoId().isEmpty())) {
                worker.setPhotoId(event.getFileStore());
                needsUpdate = true;
            }

            if (needsUpdate) {
                enrichmentService.enrichUpdate(List.of(worker), requestInfo);
                List<Worker> encrypted = workerEncryptionService.encrypt(List.of(worker), WorkerRegistryConstants.ENCRYPT_WORKER);
                workerRepository.putInCache(encrypted);
                workerRepository.update(encrypted, tenantId);
                log.info("Updated {} for worker: {}", event.getType(), workerId);
            } else {
                log.info("Worker {} already has {} set, skipping update", workerId, event.getType());
            }

        } catch (InvalidTenantIdException e) {
            throw new CustomException(WorkerRegistryConstants.INVALID_TENANT_ID_EXCEPTION, WorkerRegistryConstants.MSG_INVALID_TENANT_ID_PREFIX + tenantId);
        }
    }
}
