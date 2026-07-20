package org.egov.facility.spice.web;

import io.swagger.annotations.ApiParam;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.facility.FacilityBulkResponse;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.facility.spice.SpiceFacilitySearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Live Spice facility search: {@code POST /facility/spice/_search}.
 *
 * <p>Same request/response contract as the native facility search ({@link FacilitySearchRequest}
 * in, {@link FacilityBulkResponse} out) but resolved live from Spice for the village encoded in
 * {@code Facility.boundaryCode}. Fully separate from the existing facility flow.</p>
 */
@Slf4j
@Controller
@RequestMapping("/spice")
@Validated
public class SpiceFacilityController {

    private final SpiceFacilitySearchService service;

    @Autowired
    public SpiceFacilityController(SpiceFacilitySearchService service) {
        this.service = service;
    }

    @PostMapping("/_search")
    public ResponseEntity<FacilityBulkResponse> search(
            @ApiParam(value = "Facility search request", required = true)
            @Valid @RequestBody FacilitySearchRequest request,
            @RequestParam(value = "tenantId") String tenantId,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {

        SpiceFacilitySearchService.Result result = service.search(request, tenantId, limit, offset);

        FacilityBulkResponse response = FacilityBulkResponse.builder()
                .responseInfo(ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true))
                .facilities(result.facilities())
                .totalCount(result.totalCount())
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
