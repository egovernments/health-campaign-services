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
@Setter
@Getter
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

    @Value("${egov.boundary.hierarchy.create.endpoint}")
    private String boundaryHierarchyCreateEndpoint;

    @Value("${egov.boundary.relationship.create.endpoint}")
    private String boundaryRelationshipCreateEndpoint;
}
