package org.egov.referralmanagement.web.controllers;

import io.swagger.annotations.ApiParam;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncResponse;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.service.DownsyncPregenService;
import org.egov.referralmanagement.service.DownsyncService;
import org.egov.referralmanagement.web.models.DownsyncFileLink;
import org.egov.referralmanagement.web.models.PregenDownsyncResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/beneficiary-downsync")
@Validated
public class BeneficiaryDownsyncController {
	
	private DownsyncService downsyncService;

	private DownsyncPregenService pregenService;

	private ReferralManagementConfiguration config;

	@Autowired
	BeneficiaryDownsyncController(DownsyncService downsyncService,
								  DownsyncPregenService pregenService,
								  ReferralManagementConfiguration config) {
		this.downsyncService = downsyncService;
		this.pregenService = pregenService;
		this.config = config;
	}

    @PostMapping(value = "/v1/_get")
    public ResponseEntity<?> getBeneficiaryData(@ApiParam(value = "Capture details of Side Effect", required = true) @Valid @RequestBody DownsyncRequest request) {
		log.info("Downsync request — locality={} tenant={} lastSyncedTime={}",
				request.getDownsyncCriteria().getLocality(),
				request.getDownsyncCriteria().getTenantId(),
				request.getDownsyncCriteria().getLastSyncedTime());

		Long lastSyncedTime = request.getDownsyncCriteria().getLastSyncedTime();
		long staleThresholdMs = (long) config.getDownsyncStaleThresholdHours() * 3600 * 1000;
		boolean isStale = lastSyncedTime != null
				&& (System.currentTimeMillis() - lastSyncedTime) > staleThresholdMs;

		if (lastSyncedTime == null || isStale) {
			if (isStale)
				log.info("lastSyncedTime is older than {}h — routing to pregen path, locality={}",
						config.getDownsyncStaleThresholdHours(), request.getDownsyncCriteria().getLocality());
			List<DownsyncFileLink> links = pregenService.getPregenLinks(request.getDownsyncCriteria());
			if (links.isEmpty()) {
				log.warn("No pre-generated files for locality={} tenant={} — generation not yet run",
						request.getDownsyncCriteria().getLocality(), request.getDownsyncCriteria().getTenantId());
				throw new CustomException("PREGEN_NOT_AVAILABLE",
						"No pre-generated data available for this locality. Trigger a generation job first.");
			}
			log.info("Returning {} pre-generated file links for locality={}",
					links.size(), request.getDownsyncCriteria().getLocality());
			return ResponseEntity.ok(PregenDownsyncResponse.builder()
					.responseInfo(ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true))
					.downloadLinks(links)
					.build());
		}

        Downsync downsync = null;
        try {
            downsync = downsyncService.prepareDownsyncData(request);
        } catch (InvalidTenantIdException e) {
            log.error("Invalid tenantId: {}", ExceptionUtils.getStackTrace(e));
            throw new CustomException("INVALID_TENANT_ID", e.getMessage());
        }
        DownsyncResponse response = DownsyncResponse.builder()
                .downsync(downsync)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.ok(response);
    }
}
