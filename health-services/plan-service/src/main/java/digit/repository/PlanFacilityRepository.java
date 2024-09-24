package digit.repository;

import digit.web.models.*;

import java.util.List;

public interface PlanFacilityRepository {
    public void create(PlanFacilityRequest planFacilityRequest);

    public List<PlanFacility> search(PlanFacilitySearchCriteria planSearchCriteria);

    public void update(PlanFacilityRequest planFacilityRequest);
}
