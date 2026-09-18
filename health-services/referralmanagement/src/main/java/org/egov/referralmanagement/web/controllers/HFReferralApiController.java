package org.egov.referralmanagement.web.controllers;

import java.util.List;

import io.swagger.annotations.ApiParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkResponse;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralRequest;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralResponse;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralSearchRequest;
import org.egov.common.producer.Producer;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.service.HFReferralService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Controller class for managing HF Referrals.
 * @author  kanishq-egov
 */
@Controller
@RequestMapping("/hf-referral")
@Validated
public class HFReferralApiController {
    private final HttpServletRequest httpServletRequest;
    private final HFReferralService hfReferralService;
    private final Producer producer;
    private final ReferralManagementConfiguration referralManagementConfiguration;

    /**
     * Constructor for HFReferralApiController.
     *
     * @param httpServletRequest              The HTTP servlet request.
     * @param hfReferralService               The service for handling HFReferral operations.
     * @param producer                        The Kafka producer.
     * @param referralManagementConfiguration The configuration for referral management.
     */
    public HFReferralApiController(
            HttpServletRequest httpServletRequest,
            HFReferralService hfReferralService,
            Producer producer,
            ReferralManagementConfiguration referralManagementConfiguration
    ) {
        this.httpServletRequest = httpServletRequest;
        this.hfReferralService = hfReferralService;
        this.producer = producer;
        this.referralManagementConfiguration = referralManagementConfiguration;
    }

    /**
     * API endpoint to create a single HFReferral.
     *
     * @param request The HFReferralRequest containing referral details.
     * @return ResponseEntity containing HFReferralResponse.
     */
    @RequestMapping(value = "/v1/_create", method = RequestMethod.POST)
    public ResponseEntity<HFReferralResponse> referralV1CreatePost(@ApiParam(value = "Capture details of HFReferral", required = true) @Valid @RequestBody HFReferralRequest request) {

        HFReferral hfReferral = hfReferralService.create(request);
        HFReferralResponse response = HFReferralResponse.builder()
                .hfReferral(hfReferral)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * API endpoint to create multiple HFReferrals in bulk.
     *
     * @param request The HFReferralBulkRequest containing bulk referral details.
     * @return ResponseEntity containing ResponseInfo.
     */
    @RequestMapping(value = "/v1/bulk/_create", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> referralBulkV1CreatePost(@ApiParam(value = "Capture details of HFReferral", required = true) @Valid @RequestBody HFReferralBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        hfReferralService.putInCache(request.getHfReferrals());
        producer.push(referralManagementConfiguration.getCreateHFReferralBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    /**
     * API endpoint to search for HFReferrals based on certain criteria.
     *
     * @param request         The HFReferralSearchRequest containing search criteria.
     * @return ResponseEntity containing HFReferralBulkResponse.
     * @throws Exception
     */
    @RequestMapping(value = "/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<HFReferralBulkResponse> referralV1SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "HFReferral Search.", required = true) @Valid @RequestBody HFReferralSearchRequest request
    ) throws Exception {

        SearchResponse<HFReferral> searchResponse = hfReferralService.search(
                request,
                urlParams.getLimit(),
                urlParams.getOffset(),
                urlParams.getTenantId(),
                urlParams.getLastChangedSince(),
                urlParams.getIncludeDeleted());
        HFReferralBulkResponse response = HFReferralBulkResponse.builder().responseInfo(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true))
                .hfReferrals(searchResponse.getResponse())
                .totalCount(searchResponse.getTotalCount())
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * API endpoint to update a single HFReferral.
     *
     * @param request The HFReferralRequest containing updated referral details.
     * @return ResponseEntity containing HFReferralResponse.
     */
    @RequestMapping(value = "/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<HFReferralResponse> referralV1UpdatePost(@ApiParam(value = "Capture details of Existing HFReferral", required = true) @Valid @RequestBody HFReferralRequest request) {
        HFReferral hfReferral = hfReferralService.update(request);

        HFReferralResponse response = HFReferralResponse.builder()
                .hfReferral(hfReferral)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * API endpoint to update multiple HFReferrals in bulk.
     *
     * @param request The HFReferralBulkRequest containing bulk updated referral details.
     * @return ResponseEntity containing ResponseInfo.
     */
    @RequestMapping(value = "/v1/bulk/_update", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> referralV1BulkUpdatePost(@ApiParam(value = "Capture details of Existing HFReferral", required = true) @Valid @RequestBody HFReferralBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(referralManagementConfiguration.getUpdateHFReferralBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }

    /**
     * API endpoint to delete a single HFReferral.
     *
     * @param request The HFReferralRequest containing details of the referral to be deleted.
     * @return ResponseEntity containing HFReferralResponse.
     */
    @RequestMapping(value = "/v1/_delete", method = RequestMethod.POST)
    public ResponseEntity<HFReferralResponse> referralV1DeletePost(@ApiParam(value = "Capture details of Existing HFReferral", required = true) @Valid @RequestBody HFReferralRequest request) {
        HFReferral hfReferral = hfReferralService.delete(request);

        HFReferralResponse response = HFReferralResponse.builder()
                .hfReferral(hfReferral)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * API endpoint to delete multiple HFReferrals in bulk.
     *
     * @param request The HFReferralBulkRequest containing details of the referrals to be deleted in bulk.
     * @return ResponseEntity containing ResponseInfo.
     */
    @RequestMapping(value = "/v1/bulk/_delete", method = RequestMethod.POST)
    public ResponseEntity<ResponseInfo> referralV1BulkDeletePost(@ApiParam(value = "Capture details of Existing HFReferral", required = true) @Valid @RequestBody HFReferralBulkRequest request) {
        request.getRequestInfo().setApiId(httpServletRequest.getRequestURI());
        producer.push(referralManagementConfiguration.getDeleteHFReferralBulkTopic(), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ResponseInfoFactory
                .createResponseInfo(request.getRequestInfo(), true));
    }
}
