package org.egov.product.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ProductService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    //private final ProductRepository productRepository;

    public ProductService(){

    }


    public boolean checkProductIfExists(List<Product> productList){
//        for(Product product : productList){
//            //redisTemplate.hasKey(product.getId()) ||
//            if(productRepository.getProductById(product.getId()) != null){
//                return true;
//            }
//        }
        return false;
    }
}
