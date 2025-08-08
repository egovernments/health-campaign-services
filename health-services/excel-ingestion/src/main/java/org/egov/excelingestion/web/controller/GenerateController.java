package org.egov.excelingestion.web.controller;

import org.egov.excelingestion.web.processor.GenerateProcessorFactory;
import org.egov.excelingestion.web.models.GeneratedResource;
import org.egov.excelingestion.web.models.GeneratedResourceRequest;
import org.egov.excelingestion.web.models.GeneratedResourceResponse;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/v1/data")
public class GenerateController {

    private final GenerateProcessorFactory processorFactory;

    public GenerateController(GenerateProcessorFactory processorFactory) {
        this.processorFactory = processorFactory;
    }

        @PostMapping("/_generate")
    public ResponseEntity<GeneratedResourceResponse> generate(@RequestParam("tenantId") String tenantId,
                                                              @RequestParam("type") String type,
                                                              @RequestParam("hierarchyType") String hierarchyType,
                                                              @RequestParam("referenceId") String referenceId,
                                                              @RequestBody @Valid GeneratedResourceRequest request) {

        GeneratedResource generatedResource = request.getGeneratedResource();
        generatedResource.setTenantId(tenantId);
        generatedResource.setType(type);
        generatedResource.setHierarchyType(hierarchyType);
        generatedResource.setRefernceId(referenceId);

        GeneratedResource processedResource = processorFactory.getProcessor(type).process(request);

        ResponseInfo responseInfo = ResponseInfo.builder()
                .apiId("egov-bff")
                .ver("0.0.1")
                .ts(System.currentTimeMillis())
                .status("successful")
                .build();

        GeneratedResourceResponse response = GeneratedResourceResponse.builder()
                .responseInfo(responseInfo)
                .generatedResource(processedResource)
                .build();

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
