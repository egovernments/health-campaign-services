package org.egov.transformer.handler;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.upstream.Service;
import org.egov.transformer.service.TransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ReferralTransformationHandler implements TransformationHandler<SideEffect>{
    private final Map<Operation, List<TransformationService<SideEffect>>> operationTransformationServiceMap;

    @Autowired
    public ReferralTransformationHandler(@Qualifier("referralTransformationServiceMap")
                                            Map<Operation, List<TransformationService<SideEffect>>> operationTransformationServiceMap) {
        this.operationTransformationServiceMap = operationTransformationServiceMap;
    }

    @Override
    public void handle(List<SideEffect> payloadList, Operation operation) {
        operationTransformationServiceMap.entrySet().stream()
                .filter(e -> e.getKey().equals(operation))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream).forEach(es -> es.transform(payloadList));
    }
}
