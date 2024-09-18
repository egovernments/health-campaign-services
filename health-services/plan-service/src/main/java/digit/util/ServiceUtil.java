package digit.util;

import digit.repository.PlanConfigurationRepository;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationSearchCriteria;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ServiceUtil {

    private PlanConfigurationRepository planConfigurationRepository;

    public ServiceUtil(PlanConfigurationRepository planConfigurationRepository)
    {
        this.planConfigurationRepository = planConfigurationRepository;
    }

    /**
     * Validates the given input string against the provided regex pattern.
     *
     * @param patternString the regex pattern to validate against
     * @param inputString   the input string to be validated
     * @return true if the input string matches the regex pattern, false otherwise
     */
    public Boolean validateStringAgainstRegex(String patternString, String inputString) {
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(inputString);
        return matcher.matches();
    }

    /**
     * Searches the plan config based on the plan config id provided
     * @param planConfigId the plan config id to validate
     * @param tenantId the tenant id of the plan config
     * @return list of planConfiguration for the provided plan config id
     */
    public List<PlanConfiguration> searchPlanConfigId(String planConfigId, String tenantId)
    {
        List<PlanConfiguration> planConfigurations = planConfigurationRepository.search(PlanConfigurationSearchCriteria.builder()
                .id(planConfigId)
                .tenantId(tenantId)
                .build());

        return planConfigurations;
    }
}
