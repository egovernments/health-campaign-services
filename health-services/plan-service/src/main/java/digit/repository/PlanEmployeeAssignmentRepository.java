package digit.repository;

import digit.web.models.PlanEmployeeAssignmentRequest;

public interface PlanEmployeeAssignmentRepository {

    public void create(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest);

    public void update(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest);
}
