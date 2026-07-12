package org.egov.referralmanagement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Component
public class ReferralManagementConfiguration {
    @Value("${referralmanagement.sideeffect.kafka.create.topic}")
    private String createSideEffectTopic;

    @Value("${referralmanagement.sideeffect.kafka.update.topic}")
    private String updateSideEffectTopic;

    @Value("${referralmanagement.sideeffect.kafka.delete.topic}")
    private String deleteSideEffectTopic;

    @Value("${referralmanagement.sideeffect.consumer.bulk.create.topic}")
    private String createSideEffectBulkTopic;

    @Value("${referralmanagement.sideeffect.consumer.bulk.update.topic}")
    private String updateSideEffectBulkTopic;

    @Value("${referralmanagement.sideeffect.consumer.bulk.delete.topic}")
    private String deleteSideEffectBulkTopic;

    @Value("${egov.project.host}")
    private String projectHost;

    @Value("${egov.search.project.task.url}")
    private String projectTaskSearchUrl;

    @Value("${egov.search.project.beneficiary.url}")
    private String projectBeneficiarySearchUrl;

    @Value("${referralmanagement.referral.kafka.create.topic}")
    private String createReferralTopic;

    @Value("${referralmanagement.referral.kafka.update.topic}")
    private String updateReferralTopic;

    @Value("${referralmanagement.referral.kafka.delete.topic}")
    private String deleteReferralTopic;

    @Value("${referralmanagement.referral.consumer.bulk.create.topic}")
    private String createReferralBulkTopic;

    @Value("${referralmanagement.referral.consumer.bulk.update.topic}")
    private String updateReferralBulkTopic;

    @Value("${referralmanagement.referral.consumer.bulk.delete.topic}")
    private String deleteReferralBulkTopic;

    @Value("${referralmanagement.hfreferral.kafka.create.topic}")
    private String createHFReferralTopic;

    @Value("${referralmanagement.hfreferral.kafka.update.topic}")
    private String updateHFReferralTopic;

    @Value("${referralmanagement.hfreferral.kafka.delete.topic}")
    private String deleteHFReferralTopic;

    @Value("${referralmanagement.hfreferral.consumer.bulk.create.topic}")
    private String createHFReferralBulkTopic;

    @Value("${referralmanagement.hfreferral.consumer.bulk.update.topic}")
    private String updateHFReferralBulkTopic;

    @Value("${referralmanagement.hfreferral.consumer.bulk.delete.topic}")
    private String deleteHFReferralBulkTopic;

    @Value("${egov.search.project.facility.url}")
    private String projectFacilitySearchUrl;

    @Value("${egov.search.project.url}")
    private String projectSearchUrl;

    @Value("${egov.facility.host}")
    private String facilityHost;

    @Value("${egov.search.facility.url}")
    private String facilitySearchUrl;
    
    @Value("${egov.household.host}")
    private String householdHost;

    @Value("${egov.search.household.url}")
    private String householdSearchUrl;
    
    @Value("${egov.search.household.member.url}")
    private String householdMemberSearchUrl;
    
    @Value("${egov.individual.host}")
    private String individualHost;

    @Value("${egov.search.individual.url}")
    private String individualSearchUrl;
    
    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.search.endpoint}")
    private String mdmsSearchUrl;

    @Value("${egov.service.request.downsync.enabled:false}")
    private Boolean serviceRequestDownsyncEnabled;

    @Value("${egov.service.request.search.batch.size:100}")
    private Integer serviceRequestSearchBatchSize;

    @Value("${egov.household.member.search.batch.size:200}")
    private Integer householdMemberSearchBatchSize;

    @Value("${egov.individual.search.batch.size:200}")
    private Integer individualSearchBatchSize;

    @Value("${egov.project.beneficiary.search.batch.size:200}")
    private Integer projectBeneficiarySearchBatchSize;

    @Value("${egov.project.task.search.batch.size:200}")
    private Integer projectTaskSearchBatchSize;

    @Value("${egov.side.effect.search.batch.size:200}")
    private Integer sideEffectSearchBatchSize;

    @Value("${egov.referral.search.batch.size:200}")
    private Integer referralSearchBatchSize;

    @Value("${egov.hf.referral.search.batch.size:200}")
    private Integer hfReferralSearchBatchSize;

    @Value("${egov.service.request.host}")
    private String serviceRequestHost;

    @Value("${egov.service.request.service.search.url}")
    private String serviceRequestServiceSearchUrl;

    @Value("${egov.enable.matview.search}")
    private boolean enableMatviewSearch;

    // ── Storage backend selection ─────────────────────────────────────────────
    // Which object-storage backend downsync uses for uploads + presigned URLs.
    //   "s3"    — AWS S3 (or any S3-compatible endpoint like MinIO). Requires
    //             the six egov.s3.* properties below.
    //   "azure" — Azure Blob Storage. Requires the azure.blob.* properties.
    // Startup validation in StorageBackendValidator enforces that the required
    // set for the chosen backend is present and non-blank, failing fast otherwise.
    @Value("${egov.downsync.storage.backend:s3}")
    private String storageBackend;

    // ── AWS S3 (backend=s3) ───────────────────────────────────────────────────
    // Empty defaults so the bean container can start when backend=azure — the
    // validator refuses to boot the app if a value here is missing WHILE
    // backend=s3, so an accidental empty won't silently ship broken.
    @Value("${egov.s3.bucket:}")
    private String s3Bucket;

    @Value("${egov.s3.region:}")
    private String s3Region;

    @Value("${egov.s3.endpoint:}")
    private String s3Endpoint;

    @Value("${egov.s3.access-key:}")
    private String s3AccessKey;

    @Value("${egov.s3.secret-key:}")
    private String s3SecretKey;

    @Value("${egov.s3.presign.endpoint:}")
    private String s3PresignEndpoint;

    // ── Azure Blob Storage (backend=azure) ────────────────────────────────────
    // Azure storage account credentials + container name. Endpoint defaults to
    // the standard blob URL derived from the account name; override for
    // sovereign clouds (e.g. Azure Gov, China) or Azurite dev.
    @Value("${azure.blob.account.name:}")
    private String azureAccountName;

    @Value("${azure.blob.account.key:}")
    private String azureAccountKey;

    @Value("${azure.blob.container.name:}")
    private String azureContainerName;

    /** Optional. If blank, resolved as {@code https://<accountName>.blob.core.windows.net}. */
    @Value("${azure.blob.endpoint:}")
    private String azureBlobEndpoint;

    @Value("${egov.wardfilegen.ward.pool.size:8}")
    private int wardPoolSize;

    @Value("${egov.downsync.presigned.url.expiry.secs:86400}")
    private int presignedUrlExpirySecs;

    @Value("${egov.enc.host:}")
    private String encHost;

    @Value("${egov.enc.decrypt.endpoint:/egov-enc-service/crypto/v1/_decrypt}")
    private String encDecryptEndpoint;

    @Value("${egov.downsync.stale.threshold.hours:8}")
    private int downsyncStaleThresholdHours;

    /** When true (default), startup fails if HikariCP maximum-pool-size &lt; wardPoolSize + 4.
     *  Set to false only for local dev / tiny deployments. */
    @Value("${egov.downsync.pool.check.enabled:true}")
    private boolean poolCheckEnabled;

    /** How often the owning pod bumps lastHeartbeat on its claimed jobs. */
    @Value("${egov.downsync.heartbeat.interval.seconds:30}")
    private int heartbeatIntervalSeconds;

    /** A job is considered abandoned (claimable by another pod) if lastHeartbeat
     *  is older than this. Should be ~3× heartbeat interval to absorb one missed beat. */
    @Value("${egov.downsync.heartbeat.stale.threshold.seconds:90}")
    private int heartbeatStaleThresholdSeconds;

    /**
     * Comma-separated list of tenant schemas the app should serve. Bound to the
     * {@code SCHEMA_NAME} env var (Spring maps {@code SCHEMA_NAME → schema.name}) —
     * the same value the Helm chart passes to the db-migration init container. That
     * gives one source of truth: whatever set of schemas Flyway migrates is exactly
     * the set the running app will scan.
     *
     * <p>Only consulted when
     * {@code MultiStateInstanceUtil#getIsEnvironmentCentralInstance()} is {@code true}.
     * In non-central deployments the code emits unqualified SQL and lets the JDBC
     * connection's {@code search_path} pick the schema — {@code egov.state.level.
     * tenant.id} is a tenant identifier (not necessarily a Postgres schema name),
     * so it is deliberately not used here.
     */
    @Value("${schema.name:}")
    private String tenantSchemas;

}
