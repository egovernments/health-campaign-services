package org.egov.transformer.handler;

import org.egov.common.models.project.ProjectStaff;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.service.TransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class ProjectStaffTransformationHandler implements TransformationHandler<ProjectStaff> {

    private final Map<Operation, List<TransformationService<ProjectStaff>>> operationTransformationServiceMap;

    @Autowired
    public ProjectStaffTransformationHandler(@Qualifier("projectStaffTransformationServiceMap")
                                     Map<Operation, List<TransformationService<ProjectStaff>>> operationTransformationServiceMap) {
        this.operationTransformationServiceMap = operationTransformationServiceMap;
    }

    @Override
    public void handle(List<ProjectStaff> payloadList, Operation operation) {
        operationTransformationServiceMap.entrySet().stream()
                .filter(e -> e.getKey().equals(operation))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream).forEach(es -> es.transform(payloadList));
    }
}
