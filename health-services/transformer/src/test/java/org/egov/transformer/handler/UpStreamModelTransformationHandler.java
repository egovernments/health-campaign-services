package org.egov.transformer.handler;

import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.upstream.UpStreamModel;
import org.egov.transformer.service.TransformationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class UpStreamModelTransformationHandler implements TransformationHandler<UpStreamModel> {

    private final Map<Operation, List<TransformationService<UpStreamModel>>> operationTransformationServiceMap;

    @Autowired
    public UpStreamModelTransformationHandler(Map<Operation, List<TransformationService<UpStreamModel>>> operationTransformationServiceMap) {
        this.operationTransformationServiceMap = operationTransformationServiceMap;
    }

    @Override
    public void handle(List<UpStreamModel> payloadList, Operation operation) {
        operationTransformationServiceMap.entrySet().stream()
                .filter(e -> e.getKey().equals(operation))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream).forEach(es -> es.transform(payloadList));
    }
}
