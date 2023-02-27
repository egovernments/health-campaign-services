package org.egov.transformer.service;

import org.egov.transformer.config.SomeProperties;
import org.egov.transformer.models.downstream.DownStreamModel;
import org.egov.transformer.models.upstream.UpStreamModel;
import org.egov.transformer.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransformationServiceTest {

    private DownStreamModelTransformationService downStreamModelTransformationService;

    @Mock
    private Producer producer;

    @Mock
    private SomeProperties someProperties;

    private TransformationService.Transformer<UpStreamModel, DownStreamModel> downStreamModelTransformer;

    private List<UpStreamModel> upStreamModelList;

    @BeforeEach
    void setUp() {
        downStreamModelTransformer = new DownStreamModelTransformationService.DownStreamModelTransformer();
        downStreamModelTransformationService =
                new DownStreamModelTransformationService(downStreamModelTransformer, producer, someProperties);
        when(someProperties.getPublishDownStreamTopic()).thenReturn("some-topic");
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
    void shouldTransformTheListOfUpstreamModelsToDownStreamModels() {
        downStreamModelTransformationService.transform(upStreamModelList);

        verify(producer, times(1)).push(anyString(), anyList());
    }
}