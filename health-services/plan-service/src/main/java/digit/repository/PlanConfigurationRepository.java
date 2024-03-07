package digit.repository;


import digit.web.models.PlanConfigurationRequest;
import org.springframework.stereotype.Repository;


public interface PlanConfigurationRepository {

    public void create(PlanConfigurationRequest planConfigurationRequest);

    public void search(PlanConfigurationRequest planConfigurationRequest);

    public void update(PlanConfigurationRequest planConfigurationRequest);


}
