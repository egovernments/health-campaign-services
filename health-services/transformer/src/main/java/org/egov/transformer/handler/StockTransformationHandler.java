package org.egov.transformer.handler;

import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.upstream.Stock;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class StockTransformationHandler implements TransformationHandler<Stock> {
    @Override
    public void handle(List<Stock> payloadList, Operation operation) {

    }
}
