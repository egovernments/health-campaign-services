package org.egov.project.web.controllers;

import io.swagger.annotations.ApiParam;
import jakarta.validation.Valid;
import org.egov.project.service.BeneficiaryTeamService;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.project.web.models.team.BeneficiaryTeamAssignmentRequest;
import org.egov.project.web.models.team.BeneficiaryTeamAssignmentResponse;
import org.egov.project.web.models.team.BeneficiaryTeamAssignmentResult;
import org.egov.project.web.models.team.BeneficiaryTeamRelocationRequest;
import org.egov.project.web.models.team.BeneficiaryTeamRelocationResponse;
import org.egov.project.web.models.team.BeneficiaryTeamRelocationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@Validated
public class ProjectBeneficiaryTeamApiController {

    private final BeneficiaryTeamService beneficiaryTeamService;

    @Autowired
    public ProjectBeneficiaryTeamApiController(BeneficiaryTeamService beneficiaryTeamService) {
        this.beneficiaryTeamService = beneficiaryTeamService;
    }

    @RequestMapping(value = "/beneficiary/team/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<BeneficiaryTeamAssignmentResponse> beneficiaryTeamV1UpdatePost(@ApiParam(value = "Team code attribution for project beneficiaries, addressed either by clientReferenceId or by the users who registered them.", required = true) @Valid @RequestBody BeneficiaryTeamAssignmentRequest request) {
        BeneficiaryTeamAssignmentResult result = beneficiaryTeamService.updateTeamCode(request);
        BeneficiaryTeamAssignmentResponse response = BeneficiaryTeamAssignmentResponse.builder()
                .teamCode(result.getTeamCode())
                .matchedCount(result.getMatchedCount())
                .updatedCount(result.getUpdatedCount())
                .skippedCount(result.getSkippedCount())
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/beneficiary/team/v1/_relocate", method = RequestMethod.POST)
    public ResponseEntity<BeneficiaryTeamRelocationResponse> beneficiaryTeamV1RelocatePost(@ApiParam(value = "Moves beneficiaries onto another team code, addressed either by the team they are on now or by clientReferenceId.", required = true) @Valid @RequestBody BeneficiaryTeamRelocationRequest request) {
        BeneficiaryTeamRelocationResult result = beneficiaryTeamService.relocateTeamCode(request);
        BeneficiaryTeamRelocationResponse response = BeneficiaryTeamRelocationResponse.builder()
                .fromTeamCode(result.getFromTeamCode())
                .toTeamCode(result.getToTeamCode())
                .matchedCount(result.getMatchedCount())
                .relocatedCount(result.getRelocatedCount())
                .skippedCount(result.getSkippedCount())
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
