package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.upstream.Stock;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class StockTransformationService implements TransformationService<Stock>{
    @Override
    public void transform(List<Stock> payloadList) {

    }

    @Override
    public Operation getOperation() {
        return Operation.STOCK;
    }
}
