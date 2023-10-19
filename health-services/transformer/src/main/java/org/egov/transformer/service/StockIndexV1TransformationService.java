package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.stock.Stock;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class StockIndexV1TransformationService extends StockTransformationService{

    @Autowired
    protected StockIndexV1TransformationService(StockIndexV1Transformer transformer,
                                                Producer producer, TransformerProperties properties, CommonUtils commonUtils ) {
        super(transformer, producer, properties, commonUtils);
    }

    @Override
    public void transform(List<Stock> payloadList) {
        super.transform(payloadList);
    }

    @Override
    public String getTopic() {
        return properties.getTransformerProducerBulkStockIndexV1Topic();
    }
}
