package org.egov.project.validator.irs;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.irs.UserAction;
import org.egov.common.models.project.irs.UserActionBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.validateForNullId;
import static org.egov.project.Constants.GET_USER_ACTION;

@Component
@Order(value = 1)
@Slf4j
public class UaNullIdValidator implements Validator<UserActionBulkRequest, UserAction> {

    @Override
    public Map<UserAction, List<Error>> validate(UserActionBulkRequest request) {
        log.info("validating for null id");
        return validateForNullId(request, GET_USER_ACTION);
    }
}
