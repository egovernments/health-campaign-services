package org.egov.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.validator.Validator;
import org.egov.stock.repository.StockRepository;
import org.egov.stock.validator.stock.SisDeletedValidator;
import org.egov.stock.validator.stock.SnonExistentValidator;
import org.egov.stock.validator.stock.SnullIdValidator;
import org.egov.stock.validator.stock.SproductVaraintIdValidator;
import org.egov.stock.validator.stock.SrowVersionValidator;
import org.egov.stock.validator.stock.SuniqueEntityValidator;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
import org.egov.stock.web.models.StockRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Predicate;

@Service
@Slf4j
public class StockService {

    private final StockRepository stockRepository;

    private final List<Validator<StockBulkRequest, Stock>> validators;

    private final Predicate<Validator> isApplicableForCreate = validator -> validator.equals(SproductVaraintIdValidator.class);

    private final Predicate<Validator> isApplicableForUpdate = validator -> validator.equals(SproductVaraintIdValidator.class)
            || validator.equals(SisDeletedValidator.class)
            || validator.equals(SnonExistentValidator.class)
            || validator.equals(SnullIdValidator.class)
            || validator.equals(SrowVersionValidator.class)
            || validator.equals(SuniqueEntityValidator.class);

    private final Predicate<Validator> isApplicableForDelete = validator -> validator.equals(SnonExistentValidator.class)
            || validator.equals(SnullIdValidator.class);

    public StockService(StockRepository stockRepository, List<Validator<StockBulkRequest, Stock>> validators) {
        this.stockRepository = stockRepository;
        this.validators = validators;
    }

    public Stock create(StockRequest request) {
        return request.getStock();
    }

    public List<Stock> create(StockBulkRequest request, boolean isBulk) {
        return request.getStock();
    }

    public Stock update(StockRequest request) {
        return request.getStock();
    }

    public Stock delete(StockRequest request) {
        return request.getStock();
    }
}
