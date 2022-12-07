package org.egov.product.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ProductService {
    public boolean validateProductId(String productId) {
        return true;
    }
}
