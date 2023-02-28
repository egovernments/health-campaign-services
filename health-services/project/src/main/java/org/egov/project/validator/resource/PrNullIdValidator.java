package org.egov.project.validator.resource;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.project.web.models.ProjectResource;
import org.egov.project.web.models.ProjectResourceBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.validateForNullId;
import static org.egov.project.Constants.GET_PROJECT_RESOURCE;

@Component
@Order(value = 1)
@Slf4j
public class PrNullIdValidator implements Validator<ProjectResourceBulkRequest, ProjectResource> {
    @Override
    public Map<ProjectResource, List<Error>> validate(ProjectResourceBulkRequest request) {
        return validateForNullId(request, GET_PROJECT_RESOURCE);
    }
}
