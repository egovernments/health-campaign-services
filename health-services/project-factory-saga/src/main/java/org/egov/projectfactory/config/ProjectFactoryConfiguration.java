package org.egov.projectfactory.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralised, immutable-per-request configuration for the Project Factory (Saga) service.
 *
 * <p>Follows the project service's {@code ProjectConfiguration} pattern: a single
 * {@code @Component} holding {@code @Value}-injected properties. Multi-tenant behaviour
 * (topic prefixing, schema replacement) is provided by health-services-common's
 * {@code MultiStateInstanceUtil} and driven by the three {@code is.environment.*}
 * properties in {@code application.properties} — do not read tenant config into shared
 * mutable state (see context/02-conventions.md, the transformConfigs bug note).</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Component
public class ProjectFactoryConfiguration {

    // --- Downstream service hosts (populated as adapters are implemented) ---

    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.search.endpoint}")
    private String mdmsSearchEndpoint;

    @Value("${egov.boundary.host}")
    private String boundaryServiceHost;

    @Value("${egov.boundary.search.url}")
    private String boundarySearchUrl;

    @Value("${egov.filestore.host:}")
    private String fileStoreHost;

    // --- Search / paging defaults ---

    @Value("${search.api.limit:100}")
    private Integer searchApiLimit;

    // --- Saga engine tunables (LLD §5, §7) ---

    @Value("${pf.saga.reaper.sla.seconds:900}")
    private Long sagaReaperSlaSeconds;

    @Value("${pf.saga.reaper.fixed.delay.ms:30000}")
    private Long sagaReaperFixedDelayMs;

    @Value("${pf.saga.step.max.attempts:5}")
    private Integer sagaStepMaxAttempts;

    // --- Batch sizing (streaming pipeline / entity creation) ---

    @Value("${pf.batch.validation.chunk.size:200}")
    private Integer validationChunkSize;

    @Value("${pf.batch.upsert.size:500}")
    private Integer batchUpsertSize;

    // --- Multi-tenancy switch (mirrors platform-wide MultiStateInstanceUtil config) ---

    @Value("${is.environment.central.instance:false}")
    private Boolean centralInstance;
}
