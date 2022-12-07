package org.egov.product.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class ProductService {
    public List<String> validateProductId(List<String> productIds) {
        return Collections.emptyList();
    }
}
