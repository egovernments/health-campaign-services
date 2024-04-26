package org.egov.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;

import java.util.TimeZone;

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

    @Value("${egov.plan.config.host}")
    private String planConfigHost;

    @Value("${egov.plan.config.endpoint}")
    private String planConfigEndPoint;

    //Filestore

    @Value("${egov.filestore.host}")
    private String fileStoreHost;

    @Value("${egov.filestore.endpoint}")
    private String fileStoreEndpoint;

    @Value("${egov.filestore.upload.endpoint}")
    private String fileStoreUploadEndpoint;
}
