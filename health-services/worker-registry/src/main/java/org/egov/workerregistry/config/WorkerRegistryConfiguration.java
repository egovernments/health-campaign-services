package org.egov.workerregistry.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerRegistryConfiguration {

    // ID Generation
    @Value("${egov.idgen.host}")
    private String idgenHost;

    @Value("${egov.idgen.path}")
    private String idgenPath;

    @Value("${egov.idgen.name.worker.id}")
    private String idgenName;

    // State-level tenant
    @Value("${state.level.tenant.id}")
    private String stateLevelTenantId;
}