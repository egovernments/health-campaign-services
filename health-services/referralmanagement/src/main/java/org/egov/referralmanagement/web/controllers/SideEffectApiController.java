package org.egov.referralmanagement.web.controllers;

import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkResponse;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectResponse;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.service.SideEffectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/side-effect")
@Validated
public class SideEffectApiController {

    private final HttpServletRequest httpServletRequest;

    private final SideEffectService sideEffectService;

    private final Producer producer;

    private final ReferralManagementConfiguration referralManagementConfiguration;

    public SideEffectApiController(
            HttpServletRequest httpServletRequest,
            SideEffectService sideEffectService,
            Producer producer,
            ReferralManagementConfiguration referralManagementConfiguration
    ) {
        this.httpServletRequest = httpServletRequest;
        this.sideEffectService = sideEffectService;
        this.producer = producer;
        this.referralManagementConfiguration = referralManagementConfiguration;
    }

    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<SideEffectResponse> sideEffectV1CreatePost(@ApiParam(value = "Capture details of Side Effect", required = true) @Valid @RequestBody SideEffectRequest request) {

        SideEffect sideEffect = sideEffectService.create(request);
        SideEffectResponse response = SideEffectResponse.builder()
                .sideEffect(sideEffect)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }



    @RequestMapping(value = "/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> sideEffectBulkV1CreatePost(@ApiParam(value = "Capture details of Side Effect", required = true) @Valid @RequestBody SideEffectBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        sideEffectService.putInCache(request.getSideEffects());
        producer.push(referralManagementConfiguration.getCreateSideEffectBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<SideEffectBulkResponse> sideEffectV1SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Side Effect Search.", required = true) @Valid @RequestBody SideEffectSearchRequest request
    ) throws Exception {

        SearchResponse<SideEffect> sideEffectSearchResponse = sideEffectService.search(
                request,
                urlParams.getLimit(),
                urlParams.getOffset(),
                urlParams.getTenantId(),
                urlParams.getLastChangedSince(),
                urlParams.getIncludeDeleted()
        );
        SideEffectBulkResponse response = SideEffectBulkResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).sideEffects(sideEffectSearchResponse.getResponse())
                .totalCount(sideEffectSearchResponse.getTotalCount()).build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<SideEffectResponse> sideEffectV1UpdatePost(@ApiParam(value = "Capture details of Existing side effect", required = true) @Valid @RequestBody SideEffectRequest request) {
        SideEffect sideEffect = sideEffectService.update(request);

        SideEffectResponse response = SideEffectResponse.builder()
                .sideEffect(sideEffect)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

    }

    @RequestMapping(value = "/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> sideEffectV1BulkUpdatePost(@ApiParam(value = "Capture details of Existing side effect", required = true) @Valid @RequestBody SideEffectBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(referralManagementConfiguration.getUpdateSideEffectBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    @RequestMapping(value = "/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<SideEffectResponse> sideEffectV1DeletePost(@ApiParam(value = "Capture details of Existing side effect", required = true) @Valid @RequestBody SideEffectRequest request) {
        SideEffect sideEffect = sideEffectService.delete(request);

        SideEffectResponse response = SideEffectResponse.builder()
                .sideEffect(sideEffect)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

    }

    @RequestMapping(value = "/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> sideEffectV1BulkDeletePost(@ApiParam(value = "Capture details of Existing side effect", required = true) @Valid @RequestBody SideEffectBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(referralManagementConfiguration.getDeleteSideEffectBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }
}
