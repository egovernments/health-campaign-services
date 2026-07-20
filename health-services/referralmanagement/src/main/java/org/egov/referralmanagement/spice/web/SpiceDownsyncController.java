package org.egov.referralmanagement.spice.web;

import io.swagger.annotations.ApiParam;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncResponse;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.referralmanagement.spice.SpiceDownsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Live Spice downsync endpoint: {@code POST /referralmanagement/spice/v1/_downsync}.
 *
 * <p>Returns the same {@link Downsync} grouping the app already consumes, but built live from the
 * Spice backend (Households / Individuals / HouseholdMembers only). Fully separate from the existing
 * {@code /beneficiary-downsync/v1/_get} flow — no shared classes.</p>
 */
@Slf4j
@Controller
@RequestMapping("/spice")
@Validated
public class SpiceDownsyncController {

    private final SpiceDownsyncService spiceDownsyncService;

    @Autowired
    public SpiceDownsyncController(SpiceDownsyncService spiceDownsyncService) {
        this.spiceDownsyncService = spiceDownsyncService;
    }

    @PostMapping(value = "/v1/_downsync")
    public ResponseEntity<DownsyncResponse> downsync(
            @ApiParam(value = "Live Spice downsync request", required = true)
            @Valid @RequestBody DownsyncRequest request) {

        Downsync downsync = spiceDownsyncService.prepareDownsyncData(request);

        DownsyncResponse response = DownsyncResponse.builder()
                .downsync(downsync)
                .responseInfo(ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.ok(response);
    }
}
