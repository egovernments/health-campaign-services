package org.egov.stock.service;

import org.egov.common.validator.Validator;
import org.egov.stock.repository.StockRepository;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @InjectMocks
    private StockService stockService;

    @Mock
    private StockRepository repository;

    @Mock
    List<Validator<StockBulkRequest, Stock>> validators;
}
