package digit.repository;

import digit.web.models.PlanFacility;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.PlanFacilitySearchCriteria;

import java.util.List;

public interface PlanFacilityRepository {
    public List<PlanFacility> search(PlanFacilitySearchCriteria planSearchCriteria);

    public void update(PlanFacilityRequest planFacilityRequest);
}
