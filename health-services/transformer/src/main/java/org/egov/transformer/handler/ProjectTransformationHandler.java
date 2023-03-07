package org.egov.transformer.handler;

import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.upstream.Project;
import org.egov.transformer.service.TransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class ProjectTransformationHandler implements TransformationHandler<Project> {

    private final Map<Operation, List<TransformationService<Project>>> operationTransformationServiceMap;

    @Autowired
    public ProjectTransformationHandler(@Qualifier("projectTransformationServiceMap")
                                     Map<Operation, List<TransformationService<Project>>> operationTransformationServiceMap) {
        this.operationTransformationServiceMap = operationTransformationServiceMap;
    }

    @Override
    public void handle(List<Project> payloadList, Operation operation) {
        operationTransformationServiceMap.entrySet().stream()
                .filter(e -> e.getKey().equals(operation))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream).forEach(es -> es.transform(payloadList));
    }
}
