package digit.web.controllers;

import digit.service.QueryOrchestrationService;
import digit.util.ResponseInfoFactory;
import digit.web.models.CdlQueryRequest;
import digit.web.models.CdlQueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cdl/v1")
@Slf4j
public class CdlQueryController {

    private final QueryOrchestrationService orchestrationService;
    private final ResponseInfoFactory responseInfoFactory;

    @Autowired
    public CdlQueryController(QueryOrchestrationService orchestrationService,
                               ResponseInfoFactory responseInfoFactory) {
        this.orchestrationService = orchestrationService;
        this.responseInfoFactory = responseInfoFactory;
    }

    @PostMapping("/_query")
    public ResponseEntity<CdlQueryResponse> query(@RequestBody CdlQueryRequest request) {
        log.info("Received CDL query request: {}", request.getCdlQuery().getQueryText());

        CdlQueryResponse response = orchestrationService.processQuery(request.getCdlQuery());

        ResponseInfo responseInfo = responseInfoFactory
                .createResponseInfoFromRequestInfo(request.getRequestInfo(), true);
        response.setResponseInfo(responseInfo);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
