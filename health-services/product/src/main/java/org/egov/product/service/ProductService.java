package org.egov.product.service;

import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.product.Product;
import org.egov.common.models.product.ProductRequest;
import org.egov.common.service.IdGenService;
import org.egov.product.config.ProductConfiguration;
import org.egov.product.repository.ProductRepository;
import org.egov.product.web.models.Mdms;
import org.egov.product.web.models.ProductSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.product.config.ServiceConstants.*;
import static org.egov.product.config.ServiceConstants.JSONPATH_ERROR_MESSAGE;

@Service
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    private final IdGenService idGenService;

    private final ProductConfiguration productConfiguration;

    private final MdmsV2Service mdmsV2Service;

    private final ObjectMapper objectMapper;

    @Autowired
    public ProductService(ProductRepository productRepository, IdGenService idGenService,
                          ProductConfiguration productConfiguration, MdmsV2Service mdmsV2Service, ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.idGenService = idGenService;
        this.productConfiguration = productConfiguration;
        this.mdmsV2Service = mdmsV2Service;
        this.objectMapper = objectMapper;
    }

    public List<String> validateProductId(List<String> productIds) {
        return productRepository.validateIds(productIds, "id");
    }

    public List<Product> create(ProductRequest productRequest) throws Exception {
        log.info("Enrichment products started");

        log.info("generating ids for products");
        List<String> idList =  idGenService.getIdList(productRequest.getRequestInfo(),
                getTenantId(productRequest.getProduct()),
                "product.id", "", productRequest.getProduct().size());

        log.info("enriching products");
        enrichForCreate(productRequest.getProduct(), idList, productRequest.getRequestInfo());

        log.info("saving products");
        productRepository.save(productRequest.getProduct(), productConfiguration.getCreateProductTopic());
        return productRequest.getProduct();
    }

    public List<Product> update(ProductRequest productRequest) throws Exception {
        identifyNullIds(productRequest.getProduct());
        Map<String, Product> pMap = getIdToObjMap(productRequest.getProduct());

        log.info("checking if product already exists");
        List<String> productIds = new ArrayList<>(pMap.keySet());
        List<Product> existingProducts = productRepository.findById(productIds);

        log.info("validate entities for products");
        validateEntities(pMap, existingProducts);

        log.info("checking row version for products");
        checkRowVersion(pMap, existingProducts);

        log.info("updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(pMap, existingProducts, productRequest);

        log.info("saving updated products");
        productRepository.save(productRequest.getProduct(), productConfiguration.getUpdateProductTopic());
        return productRequest.getProduct();
    }

    public List<Product> search(ProductSearchRequest productSearchRequest,
                                Integer limit,
                                Integer offset,
                                String tenantId,
                                Long lastChangedSince,
                                Boolean includeDeleted) throws Exception {

        log.info("received request to search product");

        List <String> ids = productSearchRequest.getProduct().getId();

        List <Mdms> jsonNode = mdmsV2Service.fetchMdmsData(productSearchRequest.getRequestInfo(), tenantId, Boolean.TRUE, ids, limit, offset);

        List<Product> products= new ArrayList<>();

        jsonNode.forEach(data -> {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                // Convert JsonNode to ProductVariant object
                Product product = objectMapper.treeToValue(data.getData(), Product.class);
                products.add(product);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
            }
        });

        List <Product> filteredProduct = products.stream().filter(product -> {
            if(productSearchRequest.getProduct().getName() == null) {
                return true;
            }
            return product.getName().toLowerCase().equals(productSearchRequest.getProduct().getName().toLowerCase());
                })
                .filter(product -> {
                    if(productSearchRequest.getProduct().getManufacturer() == null) {
                        return true;
                    }
                    return product.getManufacturer().toLowerCase().equals(productSearchRequest.getProduct().getManufacturer().toLowerCase());
                })
                .filter(includeDeleted(includeDeleted))
                .filter(lastChangedSince(lastChangedSince)).collect(Collectors.toList());

        return  products;
    }
}
