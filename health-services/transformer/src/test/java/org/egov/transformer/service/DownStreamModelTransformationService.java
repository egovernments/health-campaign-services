package org.egov.transformer.service;

import org.egov.transformer.config.SomeProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.DownStreamModel;
import org.egov.transformer.models.upstream.UpStreamModel;
import org.egov.transformer.producer.Producer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DownStreamModelTransformationService implements TransformationService<UpStreamModel> {

    private final Transformer<UpStreamModel, DownStreamModel> transformer;

    private final Producer producer;

    private final SomeProperties someProperties;

    public DownStreamModelTransformationService(Transformer<UpStreamModel, DownStreamModel> transformer, Producer producer, SomeProperties someProperties) {
        this.transformer = transformer;
        this.producer = producer;
        this.someProperties = someProperties;
    }

    @Override
    public void transform(List<UpStreamModel> payloadList) {
        List<DownStreamModel> downStreamModelList = payloadList.stream()
                .map(transformer::transform)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        producer.push(someProperties.getPublishDownStreamTopic(), downStreamModelList);
    }

    @Override
    public Operation getOperation() {
        return Operation.CREATE;
    }

    static class DownStreamModelTransformer implements Transformer<UpStreamModel, DownStreamModel> {

        @Override
        public List<DownStreamModel> transform(UpStreamModel upStreamModel) {
            return Collections.singletonList(DownStreamModel.builder()
                            .modelId(upStreamModel.getId())
                            .someField(upStreamModel.getOtherField())
                    .build());
        }
    }
}
