package org.egov.product.summaryreport.web.controllers;

import io.swagger.annotations.ApiParam;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.product.summaryreport.service.SummaryReportService;
import org.egov.product.summaryreport.web.models.DailyReportSummary;
import org.egov.product.summaryreport.web.models.SummaryReportResponse;
import org.egov.product.summaryreport.web.models.SummaryReportSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Daily activity summary report for the logged-in field worker.
 * <p>
 * The worker's phone may lose its local cache (reinstall / clear data); this endpoint
 * lets the app restore the worker's own day-by-day activity (registrations, treatments,
 * stock consumed) from the server so they can reconcile against their payment.
 * <p>
 * The employee is always the caller (RequestInfo.userInfo.uuid) - a worker can only pull
 * their own summary. Reads run against the read replica.
 */
@Controller
@Validated
public class SummaryReportApiController {

    private final SummaryReportService summaryReportService;

    @Autowired
    public SummaryReportApiController(SummaryReportService summaryReportService) {
        this.summaryReportService = summaryReportService;
    }

    @RequestMapping(value = "/summary/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<SummaryReportResponse> summaryV1SearchPost(
            @ApiParam(value = "Daily summary report search criteria for the logged-in employee.", required = true)
            @Valid @RequestBody SummaryReportSearchRequest request) {

        List<DailyReportSummary> summaries = summaryReportService.getDailySummary(request);
        SummaryReportResponse response = SummaryReportResponse.builder()
                .summaryReports(summaries)
                .responseInfo(ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true))
                .build();
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
