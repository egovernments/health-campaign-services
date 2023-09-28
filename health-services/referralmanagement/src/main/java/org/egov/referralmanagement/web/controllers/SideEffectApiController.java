package org.egov.referralmanagement.web.controllers;

import io.swagger.annotations.ApiParam;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.service.SideEffectService;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkResponse;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectResponse;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

@Controller
@RequestMapping("/side_effect")
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
    public ResponseEntity<SideEffectBulkResponse> sideEffectV1SearchPost(@ApiParam(value = "Side Effect Search.", required = true) @Valid @RequestBody SideEffectSearchRequest request,
                                                                           @NotNull @Min(0) @Max(1000) @ApiParam(value = "Pagination - limit records in response", required = true) @Valid @RequestParam(value = "limit", required = true) Integer limit,
                                                                           @NotNull @Min(0) @ApiParam(value = "Pagination - offset from which records should be returned in response", required = true) @Valid @RequestParam(value = "offset", required = true) Integer offset,
                                                                           @NotNull @ApiParam(value = "Unique id for a tenant.", required = true) @Valid @RequestParam(value = "tenantId", required = true) String tenantId,
                                                                           @ApiParam(value = "epoch of the time since when the changes on the object should be picked up. Search results from this parameter should include both newly created objects since this time as well as any modified objects since this time. This criterion is included to help polling clients to get the changes in system since a last time they synchronized with the platform. ") @Valid @RequestParam(value = "lastChangedSince", required = false) Long lastChangedSince,
                                                                           @ApiParam(value = "Used in search APIs to specify if (soft) deleted records should be included in search results.", defaultValue = "false") @Valid @RequestParam(value = "includeDeleted", required = false, defaultValue = "false") Boolean includeDeleted) throws Exception {

        List<SideEffect> sideEffects = sideEffectService.search(request, limit, offset, tenantId, lastChangedSince, includeDeleted);
        SideEffectBulkResponse response = SideEffectBulkResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true)).sideEffects(sideEffects).build();

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
