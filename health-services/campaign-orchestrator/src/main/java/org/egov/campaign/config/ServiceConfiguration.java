package org.egov.campaign.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Typed configuration bean. All @Value injections live here.
 * Services inject this bean — never use @Value directly in service classes.
 *
 * Pattern from household.HouseholdConfiguration / health-notification HealthNotificationProperties.
 */
@Component
@Getter
public class ServiceConfiguration {

    // ─── Central Instance ──────────────────────────────────────────────────
    @Value("${is.environment.central.instance:false}")
    private boolean isEnvironmentCentralInstance;

    @Value("${state.level.tenantid.length:1}")
    private int stateLevelTenantIdLength;

    // ─── Kafka Topics — Campaign ───────────────────────────────────────────
    @Value("${cms.campaign.command.draft}")
    private String campaignCommandDraft;

    @Value("${cms.campaign.command.create}")
    private String campaignCommandCreate;

    @Value("${cms.campaign.event.drafted}")
    private String campaignEventDrafted;

    @Value("${cms.campaign.event.status.changed}")
    private String campaignEventStatusChanged;

    // ─── Kafka Topics — Saga ──────────────────────────────────────────────
    @Value("${cms.saga.command.start}")
    private String sagaCommandStart;

    @Value("${cms.saga.command.step.execute}")
    private String sagaCommandStepExecute;

    @Value("${cms.saga.command.step.compensate}")
    private String sagaCommandStepCompensate;

    @Value("${cms.saga.event.step.completed}")
    private String sagaEventStepCompleted;

    @Value("${cms.saga.event.step.failed}")
    private String sagaEventStepFailed;

    @Value("${cms.saga.event.completed}")
    private String sagaEventCompleted;

    @Value("${cms.saga.event.failed}")
    private String sagaEventFailed;

    // ─── Kafka Topics — Excel ─────────────────────────────────────────────
    @Value("${cms.excel.command.generate}")
    private String excelCommandGenerate;

    @Value("${cms.excel.command.process}")
    private String excelCommandProcess;

    @Value("${cms.excel.event.generation.completed}")
    private String excelEventGenerationCompleted;

    @Value("${cms.excel.event.processing.completed}")
    private String excelEventProcessingCompleted;

    // ─── Kafka Topics — Resource Workers ──────────────────────────────────
    @Value("${cms.resource.command.project.create}")
    private String resourceCommandProjectCreate;

    @Value("${cms.resource.command.user.create}")
    private String resourceCommandUserCreate;

    @Value("${cms.resource.command.facility.create}")
    private String resourceCommandFacilityCreate;

    @Value("${cms.resource.command.mapping.apply}")
    private String resourceCommandMappingApply;

    @Value("${cms.resource.event.project.created}")
    private String resourceEventProjectCreated;

    @Value("${cms.resource.event.user.created}")
    private String resourceEventUserCreated;

    @Value("${cms.resource.event.facility.created}")
    private String resourceEventFacilityCreated;

    @Value("${cms.resource.event.mapping.applied}")
    private String resourceEventMappingApplied;

    // ─── Kafka Topics — Dead Letter ───────────────────────────────────────
    @Value("${cms.dlq}")
    private String dlqTopic;

    // ─── Batch / Chunk Sizes ──────────────────────────────────────────────
    @Value("${cms.batch.boundary.chunk.size:500}")
    private int boundaryChunkSize;

    @Value("${cms.batch.user.chunk.size:100}")
    private int userChunkSize;

    @Value("${cms.batch.facility.chunk.size:100}")
    private int facilityChunkSize;

    @Value("${cms.batch.mapping.chunk.size:50}")
    private int mappingChunkSize;

    @Value("${cms.batch.project.api.batch.size:50}")
    private int projectApiBatchSize;

    @Value("${cms.batch.boundary.search.chunk.size:50}")
    private int boundarySearchChunkSize;

    @Value("${cms.batch.idgen.chunk.size:1000}")
    private int idgenChunkSize;

    // ─── Saga Config ──────────────────────────────────────────────────────
    @Value("${cms.saga.max.retry.default:3}")
    private int sagaMaxRetryDefault;

    @Value("${cms.saga.max.retry.user:5}")
    private int sagaMaxRetryUser;

    @Value("${cms.saga.max.retry.mapping:5}")
    private int sagaMaxRetryMapping;

    @Value("${cms.saga.max.reconcile.cycles:5}")
    private int sagaMaxReconcileCycles;

    @Value("${cms.saga.reconcile.stall.timeout.ms:300000}")
    private long sagaReconcileStallTimeoutMs;

    @Value("${cms.saga.step.timeout.project.ms:600000}")
    private long sagaStepTimeoutProjectMs;

    @Value("${cms.saga.step.timeout.user.ms:900000}")
    private long sagaStepTimeoutUserMs;

    @Value("${cms.saga.step.timeout.facility.ms:600000}")
    private long sagaStepTimeoutFacilityMs;

    // ─── Idempotency ──────────────────────────────────────────────────────
    @Value("${cms.idempotency.ttl.days:7}")
    private int idempotencyTtlDays;

    // ─── External Service URLs ─────────────────────────────────────────────
    @Value("${egov.hrms.host}")
    private String hrmsHost;

    @Value("${egov.hrms.employee.create.url}")
    private String hrmsEmployeeCreateUrl;

    @Value("${egov.hrms.employee.search.url}")
    private String hrmsEmployeeSearchUrl;

    @Value("${egov.project.host}")
    private String projectHost;

    @Value("${egov.project.create.url}")
    private String projectCreateUrl;

    @Value("${egov.project.search.url}")
    private String projectSearchUrl;

    @Value("${egov.project.staff.create.url}")
    private String projectStaffCreateUrl;

    @Value("${egov.project.staff.search.url}")
    private String projectStaffSearchUrl;

    @Value("${egov.project.staff.delete.url}")
    private String projectStaffDeleteUrl;

    @Value("${egov.project.facility.create.url}")
    private String projectFacilityCreateUrl;

    @Value("${egov.project.facility.search.url}")
    private String projectFacilitySearchUrl;

    @Value("${egov.project.facility.delete.url}")
    private String projectFacilityDeleteUrl;

    @Value("${egov.project.resource.create.url}")
    private String projectResourceCreateUrl;

    @Value("${egov.project.resource.search.url}")
    private String projectResourceSearchUrl;

    @Value("${egov.facility.host}")
    private String facilityHost;

    @Value("${egov.facility.create.url}")
    private String facilityCreateUrl;

    @Value("${egov.facility.search.url}")
    private String facilitySearchUrl;

    @Value("${egov.boundary.host}")
    private String boundaryHost;

    @Value("${egov.boundary.search.url}")
    private String boundarySearchUrl;

    @Value("${egov.idgen.host}")
    private String idgenHost;

    @Value("${egov.idgen.generate.url}")
    private String idgenGenerateUrl;

    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.v2.host}")
    private String mdmsV2Host;

    @Value("${egov.localization.host}")
    private String localizationHost;

    @Value("${egov.filestore.host}")
    private String filestoreHost;

    @Value("${egov.filestore.upload.url}")
    private String filestoreUploadUrl;

    @Value("${egov.filestore.download.url}")
    private String filestoreDownloadUrl;

    @Value("${egov.worker.registry.host}")
    private String workerRegistryHost;

    @Value("${egov.worker.registry.create.url}")
    private String workerRegistryCreateUrl;

    @Value("${egov.worker.registry.search.url}")
    private String workerRegistrySearchUrl;
}
