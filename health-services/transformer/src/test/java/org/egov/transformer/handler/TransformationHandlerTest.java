package org.egov.transformer.handler;

import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.DownStreamModel;
import org.egov.transformer.models.upstream.UpStreamModel;
import org.egov.transformer.service.TransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

@Disabled
@ExtendWith(MockitoExtension.class)
class TransformationHandlerTest {
    private UpStreamModelTransformationHandler transformationHandler;

    @Mock
    private TransformationService<DownStreamModel> downStreamModelTransformationService;

    private List<UpStreamModel> upStreamModelList;

    @BeforeEach
    void setUp() {
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
    }
}