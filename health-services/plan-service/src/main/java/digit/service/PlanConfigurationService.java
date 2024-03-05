package digit.service;

import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PlanConfigurationService {

    @Autowired
    public PlanConfigurationService() {
    }

    public PlanConfigurationRequest create(PlanConfigurationRequest request) {
        log.info("received request to create plan configurations");

        return new PlanConfigurationRequest();
    }
}