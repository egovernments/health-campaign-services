package digit.repository;


import digit.web.models.PlanConfigurationRequest;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanConfigurationRepository {

    public void create(PlanConfigurationRequest planConfigurationRequest);

    public void update(PlanConfigurationRequest planConfigurationRequest);

}
