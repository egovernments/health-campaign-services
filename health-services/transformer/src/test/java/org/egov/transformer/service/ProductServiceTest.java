package org.egov.transformer.service;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductServiceTest {

    @Test
    void nullProductVariantIdReturnsNullNameWithoutLookup() {
        // No properties/client: any attempt to look the variant up would throw.
        ProductService productService = new ProductService(null, null, null);

        List<String> names = productService.getProductVariantNames(Collections.singletonList(null), "demo");

        assertEquals(1, names.size());
        assertNull(names.get(0));
    }
}
