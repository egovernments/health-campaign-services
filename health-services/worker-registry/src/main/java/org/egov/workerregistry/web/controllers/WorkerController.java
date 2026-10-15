package org.egov.workerregistry.web.controllers;

import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.workerregistry.service.WorkerService;
import org.egov.workerregistry.web.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
@Validated
public class WorkerController {

    private final WorkerService workerService;

    @Autowired
    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @PostMapping("/bulk/_create")
    public ResponseEntity<WorkerResponse> bulkCreate(@RequestBody @Valid WorkerBulkRequest request) {
        List<Worker> workers = workerService.create(request);
        ResponseInfo responseInfo = ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true);
        WorkerResponse response = WorkerResponse.builder()
                .responseInfo(responseInfo)
                .workers(workers)
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/bulk/_update")
    public ResponseEntity<WorkerResponse> bulkUpdate(@RequestBody @Valid WorkerBulkRequest request) {
        List<Worker> workers = workerService.update(request);
        ResponseInfo responseInfo = ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true);
        WorkerResponse response = WorkerResponse.builder()
                .responseInfo(responseInfo)
                .workers(workers)
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/_search")
    public ResponseEntity<WorkerResponse> search(@RequestBody @Valid WorkerSearchRequest request) {
        List<Worker> workers = workerService.search(request);
        ResponseInfo responseInfo = ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true);
        WorkerResponse response = WorkerResponse.builder()
                .responseInfo(responseInfo)
                .workers(workers)
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/individual/bulk/_create")
    public ResponseEntity<ResponseInfo> mapIndividual(@RequestBody @Valid WorkerIndividualMapBulkRequest request) {
        workerService.mapIndividual(request);
        ResponseInfo responseInfo = ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(responseInfo);
    }
}
