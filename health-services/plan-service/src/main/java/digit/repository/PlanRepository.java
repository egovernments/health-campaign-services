package digit.repository;

import digit.web.models.PlanRequest;
import digit.web.models.PlanSearchCriteria;

public interface PlanRepository {
    public void create(PlanRequest planRequest);

    public void search(PlanSearchCriteria planSearchCriteria);

    public void update(PlanRequest planRequest);

}
