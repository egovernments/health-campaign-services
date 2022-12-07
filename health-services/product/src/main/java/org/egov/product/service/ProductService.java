package org.egov.product.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.Product;
import org.egov.product.web.models.ProductRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@Slf4j
public class ProductService {
    private final Producer producer;

    private ProductRepository productRepository;

    private RedisTemplate<String, Object> redisTemplate;

    private final String HASH_KEY = "PRODUCT";
    @Autowired
    public ProductService(Producer producer, ProductRepository productRepository, RedisTemplate<String, Object> redisTemplate) {
        this.producer = producer;
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
    }

    public List<Product> create(ProductRequest productRequest) throws Exception {
//        Optional<Product> anyProduct = productRequest.getProduct()
//                .stream().findAny();
//        String tenantId = null;
//        if (anyProduct.isPresent()) {
//            tenantId = anyProduct.get().getTenantId();
//        }

        //Throws exception if found in DB / cache
        checkProductExists(productRequest.getProduct());



        //Generate Audit Details if not present.

        //cache DATA.
        cache(productRequest.getProduct());

        producer.push("health-product-topic", productRequest);

        return productRequest.getProduct();
    }

    private void cache(List<Product> products) {
        for(Product product: products){
            redisTemplate.opsForHash().put(HASH_KEY, product.getId(), product);
        }
    }

    private void checkProductExists(List<Product> products) throws Exception {
        boolean foundInCache = products.stream().map((Product p) -> p.getId() != null && redisTemplate.hasKey(p.getId())).reduce(true,
                (element1, element2) -> element1 && element2);

        boolean foundInDb = products.stream().map((Product p) -> p.getId() != null && productRepository.checkIfExist(p)).reduce(true,
                (element1, element2) -> element1 && element2);

        if(foundInDb || foundInCache){
            throw new Exception("ALREADY_EXISTS");
        }
    }
}
