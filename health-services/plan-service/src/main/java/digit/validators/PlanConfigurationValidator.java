package digit.validators;

import digit.web.models.PlanConfigurationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PlanConfigurationValidator {

    public void validateCreate(PlanConfigurationRequest request)  {
//        enrichPlanConfiguration(request.getPlanConfiguration());
    }
}
