package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.excelingestion.cache.GenerationCacheService;
import org.egov.excelingestion.config.KafkaTopicConfig;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.repository.GeneratedFileRepository;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.GenerationSearchCriteria;
import org.egov.excelingestion.web.models.GenerationSearchRequest;
import org.egov.excelingestion.web.models.GenerationSearchResponse;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.producer.Producer;
import org.egov.common.exception.InvalidTenantIdException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GenerationService {

    private final GeneratedFileRepository generatedFileRepository;
    private final Producer producer;
    private final ExcelGenerationValidationService validationService;
    private final RequestInfoConverter requestInfoConverter;
    private final KafkaTopicConfig kafkaTopicConfig;
    private final GenerationCacheService generationCacheService;

    public GenerationService(GeneratedFileRepository generatedFileRepository,
                             Producer producer,
                             ExcelGenerationValidationService validationService,
                             RequestInfoConverter requestInfoConverter,
                             KafkaTopicConfig kafkaTopicConfig,
                             GenerationCacheService generationCacheService) {
        this.generatedFileRepository = generatedFileRepository;
        this.producer = producer;
        this.validationService = validationService;
        this.requestInfoConverter = requestInfoConverter;
        this.kafkaTopicConfig = kafkaTopicConfig;
        this.generationCacheService = generationCacheService;
    }

    /**
     * Init is now strictly async:
     *  - validate synchronously (so bad requests get an immediate 4xx)
     *  - expire ALL prior non-expired records for (tenantId, referenceId, type) - this
     *    is what implements the "retry invalidates old rows" requirement
     *  - publish save event with status = QUEUED so the persister creates the row
     *  - invalidate the by-reference Redis cache so the next search rehydrates
     *  - publish the GenerateResourceRequest to the init Kafka topic; the in-process
     *    consumer (single poll, manual ack) drives the actual generation
     */
    public String initiateGeneration(GenerateResourceRequest request) {
        String generationId = UUID.randomUUID().toString();

        GenerateResource generateResource = request.getGenerateResource();
        generateResource.setId(generationId);
        generateResource.setStatus(GenerationConstants.STATUS_QUEUED);

        long now = System.currentTimeMillis();
        AuditDetails auditDetails = AuditDetails.builder()
                .createdTime(now)
                .lastModifiedTime(now)
                .build();

        if (request.getRequestInfo() != null && request.getRequestInfo().getUserInfo() != null) {
            String userUuid = request.getRequestInfo().getUserInfo().getUuid();
            auditDetails.setCreatedBy(userUuid);
            auditDetails.setLastModifiedBy(userUuid);
        }
        generateResource.setAuditDetails(auditDetails);
        generateResource.setCreatedTime(now);
        generateResource.setLastModifiedTime(now);

        if (request.getRequestInfo() != null) {
            String locale = generateResource.getLocale() != null ? generateResource.getLocale()
                    : requestInfoConverter.extractLocale(request.getRequestInfo());
            generateResource.setLocale(locale);
        }

        try {
            log.info("Performing pre-generation validations for id: {}", generationId);
            validationService.validate(generateResource, request.getRequestInfo());
            log.info("Pre-generation validations completed for id: {}", generationId);

            // Retry semantics: drop any prior records for the same (tenant, reference, type)
            // so the new run is the single live record.
            expirePreviousRecords(generateResource);

            // Persister will create the row from this event.
            producer.push(generateResource.getTenantId(),
                    kafkaTopicConfig.getGenerationSaveTopic(),
                    generateResource);

            // Cache becomes stale the moment we write a new save event - drop it so the
            // next search picks up the just-queued row.
            generationCacheService.invalidate(generateResource.getTenantId(), generateResource.getReferenceId());

            // Hand off to the async consumer. The HTTP call returns as soon as this push
            // succeeds; no work happens on the request thread.
            GenerateResourceRequest initEvent = GenerateResourceRequest.builder()
                    .requestInfo(request.getRequestInfo())
                    .generateResource(generateResource)
                    .build();
            producer.push(generateResource.getTenantId(),
                    kafkaTopicConfig.getGenerationInitTopic(),
                    initEvent);

            log.info("Generation queued with id: {} for tenantId: {} (topic: {})",
                    generationId,
                    generateResource.getTenantId(),
                    kafkaTopicConfig.getGenerationInitTopic());
            return generationId;
        } catch (org.egov.tracer.model.CustomException e) {
            log.error("Validation failed for generation id: {} - Error: {}", generationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error initiating generation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initiate generation", e);
        }
    }

    public GenerationSearchResponse searchGenerations(GenerationSearchRequest request) throws InvalidTenantIdException {
        try {
            GenerationSearchCriteria criteria = request.getGenerationSearchCriteria();

            if (criteria.getLimit() == null) {
                criteria.setLimit(50);
            }
            if (criteria.getOffset() == null) {
                criteria.setOffset(0);
            }

            if (request.getRequestInfo() != null) {
                String locale = criteria.getLocale() != null ? criteria.getLocale()
                        : requestInfoConverter.extractLocale(request.getRequestInfo());
                criteria.setLocale(locale);
            }

            List<GenerateResource> results = searchWithCache(criteria);
            int totalCount = results.size();

            // Apply pagination after cache-aware fetch so cached list stays whole.
            List<GenerateResource> page = paginate(results, criteria.getOffset(), criteria.getLimit());

            ResponseInfo responseInfo = ResponseInfo.builder()
                    .apiId(request.getRequestInfo().getApiId())
                    .ver(request.getRequestInfo().getVer())
                    .ts(request.getRequestInfo().getTs())
                    .status("successful")
                    .build();

            return GenerationSearchResponse.builder()
                    .responseInfo(responseInfo)
                    .generationDetails(page)
                    .totalCount(totalCount)
                    .build();

        } catch (InvalidTenantIdException e) {
            log.error("Invalid tenant ID in search request: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error searching generations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search generations", e);
        }
    }

    /**
     * Cache-aware search. We only consult Redis for the common project-factory
     * pattern (search by a single tenant+referenceId), because that's the access
     * pattern that benefits from per-reference invalidation. All other criteria
     * fall through to the DB - simpler and safe.
     */
    private List<GenerateResource> searchWithCache(GenerationSearchCriteria criteria) throws InvalidTenantIdException {
        boolean cacheable = isSingleReferenceLookup(criteria);
        String tenantId = criteria.getTenantId();
        String referenceId = cacheable ? criteria.getReferenceIds().get(0) : null;

        if (cacheable) {
            List<GenerateResource> cached = generationCacheService.getByReference(tenantId, referenceId);
            if (cached != null) {
                log.debug("Generation search cache HIT for tenantId={} referenceId={}", tenantId, referenceId);
                return filterAndSort(cached, criteria);
            }
            log.debug("Generation search cache MISS for tenantId={} referenceId={}", tenantId, referenceId);
        }

        // Hit DB without pagination/sort filters so we can populate the by-ref cache
        // with the full live set. The DB query already orders by createdTime DESC.
        GenerationSearchCriteria fetchCriteria = criteria;
        if (cacheable) {
            fetchCriteria = GenerationSearchCriteria.builder()
                    .tenantId(tenantId)
                    .referenceIds(criteria.getReferenceIds())
                    .build();
        }
        List<GenerateResource> dbResults = generatedFileRepository.search(fetchCriteria);

        if (cacheable) {
            generationCacheService.putByReference(tenantId, referenceId, dbResults);
            return filterAndSort(dbResults, criteria);
        }
        return sortByLastModifiedDesc(dbResults);
    }

    private boolean isSingleReferenceLookup(GenerationSearchCriteria criteria) {
        return criteria.getTenantId() != null
                && criteria.getReferenceIds() != null
                && criteria.getReferenceIds().size() == 1
                && (criteria.getIds() == null || criteria.getIds().isEmpty())
                && (criteria.getAdditionalDetails() == null || criteria.getAdditionalDetails().isEmpty());
    }

    private List<GenerateResource> filterAndSort(List<GenerateResource> records, GenerationSearchCriteria criteria) {
        Set<String> types = criteria.getTypes() == null ? null : new java.util.HashSet<>(criteria.getTypes());
        Set<String> statuses = criteria.getStatuses() == null ? null : new java.util.HashSet<>(criteria.getStatuses());
        Set<String> referenceTypes = criteria.getReferenceTypes() == null ? null : new java.util.HashSet<>(criteria.getReferenceTypes());
        String locale = criteria.getLocale();

        List<GenerateResource> filtered = records.stream()
                .filter(r -> types == null || (r.getType() != null && types.contains(r.getType())))
                .filter(r -> statuses == null || (r.getStatus() != null && statuses.contains(r.getStatus())))
                .filter(r -> referenceTypes == null || (r.getReferenceType() != null && referenceTypes.contains(r.getReferenceType())))
                .filter(r -> locale == null || locale.isEmpty() || locale.equals(r.getLocale()))
                .collect(Collectors.toList());

        return sortByLastModifiedDesc(filtered);
    }

    private List<GenerateResource> sortByLastModifiedDesc(List<GenerateResource> records) {
        List<GenerateResource> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparing(
                (GenerateResource r) -> {
                    if (r.getLastModifiedTime() != null) return r.getLastModifiedTime();
                    if (r.getAuditDetails() != null && r.getAuditDetails().getLastModifiedTime() != null) {
                        return r.getAuditDetails().getLastModifiedTime();
                    }
                    return 0L;
                },
                Comparator.reverseOrder()));
        return sorted;
    }

    private List<GenerateResource> paginate(List<GenerateResource> records, Integer offset, Integer limit) {
        int from = offset == null ? 0 : Math.max(0, offset);
        if (from >= records.size()) {
            return new ArrayList<>();
        }
        int to = limit == null ? records.size() : Math.min(records.size(), from + limit);
        return new ArrayList<>(records.subList(from, to));
    }

    /**
     * Expire every prior non-expired record for (tenantId, referenceId, type).
     * This is the hook the "retry invalidates old rows" requirement relies on -
     * a fresh init always supersedes anything that was queued, running, completed
     * or failed earlier for the same key.
     */
    private void expirePreviousRecords(GenerateResource newGenerateResource) {
        try {
            String referenceId = newGenerateResource.getReferenceId();
            String type = newGenerateResource.getType();
            String tenantId = newGenerateResource.getTenantId();

            if (referenceId == null || type == null) {
                log.warn("ReferenceId or type is null, skipping expiry of previous records");
                return;
            }

            GenerationSearchCriteria criteria = GenerationSearchCriteria.builder()
                    .tenantId(tenantId)
                    .referenceIds(Arrays.asList(referenceId))
                    .types(Arrays.asList(type))
                    .statuses(Arrays.asList(
                            GenerationConstants.STATUS_QUEUED,
                            GenerationConstants.STATUS_PENDING,
                            GenerationConstants.STATUS_IN_PROGRESS,
                            GenerationConstants.STATUS_COMPLETED,
                            GenerationConstants.STATUS_FAILED))
                    .build();

            List<GenerateResource> existing = generatedFileRepository.search(criteria);
            if (existing == null || existing.isEmpty()) {
                return;
            }

            log.info("Expiring {} prior generation records for referenceId={} type={}",
                    existing.size(), referenceId, type);

            long now = System.currentTimeMillis();
            for (GenerateResource record : existing) {
                record.setStatus(GenerationConstants.STATUS_EXPIRED);
                record.setLastModifiedTime(now);
                record.setLastModifiedBy(newGenerateResource.getLastModifiedBy());
                if (record.getAuditDetails() != null) {
                    record.getAuditDetails().setLastModifiedTime(now);
                    record.getAuditDetails().setLastModifiedBy(newGenerateResource.getLastModifiedBy());
                }
                producer.push(tenantId, kafkaTopicConfig.getGenerationUpdateTopic(), record);
                log.info("Expired generation record id={}", record.getId());
            }

            generationCacheService.invalidate(tenantId, referenceId);
        } catch (Exception e) {
            // Expiring prior records is best-effort; a new run is still safe to proceed.
            log.error("Error expiring previous records: {}", e.getMessage(), e);
        }
    }
}
