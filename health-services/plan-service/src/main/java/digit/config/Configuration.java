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

    @Value("${egov.mdms.search.v2.endpoint}")
    private String mdmsV2EndPoint;

    //Persister Topic
    @Value("${plan.configuration.create.topic}")
    private String planConfigCreateTopic;

    @Value("${plan.configuration.update.topic}")
    private String planConfigUpdateTopic;

    @Value("${plan.create.topic}")
    private String planCreateTopic;

    @Value("${plan.update.topic}")
    private String planUpdateTopic;

    @Value("${plan.default.offset}")
    private Integer defaultOffset;

    @Value("${plan.default.limit}")
    private Integer defaultLimit;

}
