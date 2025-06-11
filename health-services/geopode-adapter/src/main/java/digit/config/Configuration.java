package digit.config;

import lombok.*;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@Component
@Data
@Import({TracerConfiguration.class})
@NoArgsConstructor
@AllArgsConstructor

public class Configuration {

    //MDMS
    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.search.endpoint}")
    private String mdmsEndPoint;

    //Boundary Service
    @Value("${egov.boundary.service.host}")
    private String boundaryServiceHost;

    @Value("${egov.boundary.entity.create.endpoint}")
    private String boundaryEntityCreateEndpoint;

    @Value("${egov.boundary.entity.search.endpoint}")
    private String boundaryEntitySearchEndpoint;

    @Value("${egov.boundary.hierarchy.create.endpoint}")
    private String boundaryHierarchyCreateEndpoint;

    @Value("${egov.boundary.relationship.create.endpoint}")
    private String boundaryRelationshipCreateEndpoint;

    @Value("${egov.boundary.hierarchy.search.endpoint}")
    private String boundaryHierarchySearchEndpoint;

    @Value("${geopode.arcgis.host}")
    private String arcgisHost;

    @Value("${geopode.arcgis.endpoint}")
    private String arcgisEnpoint;

    @Value("${egov.mdms.v2.search.endpoint}")
    private String mdmsV2EndPoint;

    @Value("${geopode.default.offset}")
    private String defaultOffset;

    @Value("${geopode.default.limit}")
    private String defaultLimit;

    @Value("${egov.mdms.tenantId}")
    private String tenantId;

    @Value("${egov.mdms.schemaCode}")
    private String schemaCode;

    @Value("${geopode.localHost}")
    private Integer geopodeLocalHost;

    @Value("${geopode.arcgis.search}")
    private Integer geopodeSearchEndpoint;
}
