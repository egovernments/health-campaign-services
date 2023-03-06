package org.egov.transformer.handler;

import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.upstream.UpStreamModel;
import org.egov.transformer.service.TransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransformationHandlerTest {
    private UpStreamModelTransformationHandler transformationHandler;

    @Mock
    private TransformationService<UpStreamModel> downStreamModelTransformationService;

    private List<UpStreamModel> upStreamModelList;

    @BeforeEach
    void setUp() {
        Map<Operation, List<TransformationService<UpStreamModel>>> operationTransformationServiceMap = new HashMap<>();
        operationTransformationServiceMap.put(Operation.CREATE,
                Collections.singletonList(downStreamModelTransformationService));
        transformationHandler = new UpStreamModelTransformationHandler(operationTransformationServiceMap);
        upStreamModelList = Arrays.asList(
                UpStreamModel.builder()
                        .id("some-id")
                        .otherField("other-field")
                        .build(),
                UpStreamModel.builder()
                        .id("some-id")
                        .otherField("other-field")
                        .build()
        );
    }

    @Test
    @DisplayName("should handle transformation for a particular model successfully")
    void shouldHandleTransformationForAParticularModelSuccessfully() {
        transformationHandler.handle(upStreamModelList, Operation.CREATE);

        verify(downStreamModelTransformationService, times(1))
                .transform(upStreamModelList);
    }
}