package org.egov.excelingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.cache.CacheManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.TimeUnit;

@SpringBootApplication
@ComponentScan(basePackages = {"org.egov"})
@Import({TracerConfiguration.class})
@EnableCaching
@EnableAsync
@EnableScheduling
public class ExcelIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExcelIngestionApplication.class, args);
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Create individual caches with different expiration times
        com.github.benmanes.caffeine.cache.Cache<Object, Object> localizationCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(100)
                .build();
        
        com.github.benmanes.caffeine.cache.Cache<Object, Object> boundaryHierarchyCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(100)
                .build();
        
        com.github.benmanes.caffeine.cache.Cache<Object, Object> boundaryRelationshipCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(100)
                .build();
        
        com.github.benmanes.caffeine.cache.Cache<Object, Object> excelSheetDataCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();
        
        com.github.benmanes.caffeine.cache.Cache<Object, Object> enrichedBoundaryCodesCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();
        
        com.github.benmanes.caffeine.cache.Cache<Object, Object> enrichedBoundaryObjectsCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();
        
        com.github.benmanes.caffeine.cache.Cache<Object, Object> enrichedBoundaryCodesWithoutRootCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();
        
        com.github.benmanes.caffeine.cache.Cache<Object, Object> mdmsExcelIngestionProcessCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(50)
                .build();
        
        com.github.benmanes.caffeine.cache.Cache<Object, Object> mdmsExcelIngestionGenerateCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(50)
                .build();

        // MDMS schema definitions (HCM-ADMIN-CONSOLE.schemas) - stable config, fetched once per
        // (tenant, schemaName) instead of on every upload. Mirrors the other mdms config caches.
        com.github.benmanes.caffeine.cache.Cache<Object, Object> mdmsSchemasCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();

        // Campaign lookups - the same campaign is otherwise re-fetched several times per generate/upload
        // (user + facility processors + boundary util). Keyed by campaignId + tenantId. Three separate
        // regions because the cached return types differ (detail / boundary list / projectType string).
        com.github.benmanes.caffeine.cache.Cache<Object, Object> campaignDetailCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(50)
                .build();

        com.github.benmanes.caffeine.cache.Cache<Object, Object> campaignBoundariesCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(50)
                .build();

        com.github.benmanes.caffeine.cache.Cache<Object, Object> campaignProjectTypeCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(50)
                .build();

        // Register individual caches
        cacheManager.registerCustomCache("localizationMessages", localizationCache);
        cacheManager.registerCustomCache("boundaryHierarchy", boundaryHierarchyCache);
        cacheManager.registerCustomCache("boundaryRelationship", boundaryRelationshipCache);
        cacheManager.registerCustomCache("excelSheetData", excelSheetDataCache);
        cacheManager.registerCustomCache("enrichedBoundaryCodes", enrichedBoundaryCodesCache);
        cacheManager.registerCustomCache("enrichedBoundaryObjects", enrichedBoundaryObjectsCache);
        cacheManager.registerCustomCache("enrichedBoundaryCodesWithoutRoot", enrichedBoundaryCodesWithoutRootCache);
        cacheManager.registerCustomCache("mdmsExcelIngestionProcess", mdmsExcelIngestionProcessCache);
        cacheManager.registerCustomCache("mdmsExcelIngestionGenerate", mdmsExcelIngestionGenerateCache);
        cacheManager.registerCustomCache("mdmsSchemas", mdmsSchemasCache);
        cacheManager.registerCustomCache("campaignDetail", campaignDetailCache);
        cacheManager.registerCustomCache("campaignBoundaries", campaignBoundariesCache);
        cacheManager.registerCustomCache("campaignProjectType", campaignProjectTypeCache);

        return cacheManager;
    }

    /**
     * Bounded executor for the heavy @Async upload-processing pipeline
     * ({@code AsyncProcessingService#processExcelAsync}). One task can hold several full
     * XSSF workbook DOMs at once (uploaded file + immutable-join baseline) plus the
     * serialized output bytes, so only a couple of tasks fit safely in the heap at a time.
     * Template generation is NOT on this pool - it runs synchronously on the single-record
     * Kafka listener thread - so worst-case concurrent heavy work is maxPoolSize + 1.
     * Excess tasks wait in the bounded queue; if the queue is ever full, CallerRunsPolicy
     * runs the task on the submitting thread (backpressure) instead of stacking more
     * concurrent heap usage or silently rejecting the upload.
     */
    @Bean(name = "taskExecutor")
    public java.util.concurrent.Executor taskExecutor() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);      // serialize heavy Excel tasks: one at a time
        executor.setMaxPoolSize(1);       // never run two concurrently -> bounds peak heap (avoids 2x workbook DOM OOM)
        executor.setQueueCapacity(100);   // queued tasks are cheap (no workbook loaded yet)
        executor.setThreadNamePrefix("excel-async-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}

