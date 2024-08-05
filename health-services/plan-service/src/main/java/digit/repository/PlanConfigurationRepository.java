package digit.repository;


import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationRequest;
import digit.web.models.PlanConfigurationSearchCriteria;
import java.util.List;
import org.springframework.stereotype.Repository;


public interface PlanConfigurationRepository {

    public void create(PlanConfigurationRequest planConfigurationRequest);

    public List<PlanConfiguration> search(PlanConfigurationSearchCriteria planConfigurationSearchCriteria);

    public void update(PlanConfigurationRequest planConfigurationRequest);


}
