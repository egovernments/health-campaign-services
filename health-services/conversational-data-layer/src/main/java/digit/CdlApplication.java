package digit;

import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

@Import({TracerConfiguration.class})
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class CdlApplication {

    public static void main(String[] args) {
        SpringApplication.run(CdlApplication.class, args);
    }
}
