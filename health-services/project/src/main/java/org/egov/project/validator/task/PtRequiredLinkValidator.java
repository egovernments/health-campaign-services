package org.egov.project.validator.task;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/**
 * Structural link validation for tasks. With existence validation unbundled (flags off), a task
 * saved without any beneficiary link is an orphan delivery that can never be traced back to a
 * person. The database currently permits a null clientReferenceId, but accepting one would make
 * offline reconciliation and record-level recovery unreliable. Presence of the links is therefore
 * enforced even though the referenced beneficiary is allowed to not exist yet. Always on:
 * structural, not an existence check.
 */
@Component
@Order(value = 1)
@Slf4j
public class PtRequiredLinkValidator implements Validator<TaskBulkRequest, Task> {

    private static final String ERROR_CODE = "REQUIRED_LINK_MISSING";

    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        for (Task task : request.getTasks()) {
            List<String> missing = new ArrayList<>();
            if (StringUtils.isBlank(task.getClientReferenceId())) {
                missing.add("clientReferenceId");
            }
            if (StringUtils.isBlank(task.getProjectBeneficiaryId())
                    && StringUtils.isBlank(task.getProjectBeneficiaryClientReferenceId())) {
                missing.add("projectBeneficiaryId/projectBeneficiaryClientReferenceId");
            }
            if (!missing.isEmpty()) {
                String message = "Required link field(s) missing: " + String.join(", ", missing);
                Error error = Error.builder()
                        .errorMessage(message)
                        .errorCode(ERROR_CODE)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(ERROR_CODE, message))
                        .build();
                log.error("task {} rejected: {}", task.getClientReferenceId(), message);
                populateErrorDetails(task, error, errorDetailsMap);
            }
        }
        return errorDetailsMap;
    }
}
