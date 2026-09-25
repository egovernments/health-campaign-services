package org.egov.individual.web.controllers;

import java.util.List;

import io.swagger.annotations.ApiParam;
import jakarta.validation.Valid;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.individual.service.TeamCodeService;
import org.egov.individual.web.models.TeamAssignmentRequest;
import org.egov.individual.web.models.TeamAssignmentResponse;
import org.egov.individual.web.models.TeamAssignmentResult;
import org.egov.individual.web.models.TeamCode;
import org.egov.individual.web.models.TeamCodeGenerationRequest;
import org.egov.individual.web.models.TeamCodeGenerationResponse;
import org.egov.individual.web.models.TeamCodeSearchRequest;
import org.egov.individual.web.models.TeamCodeSearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Validated
public class IndividualTeamApiController {

    private final TeamCodeService teamCodeService;

    @Autowired
    public IndividualTeamApiController(TeamCodeService teamCodeService) {
        this.teamCodeService = teamCodeService;
    }

    @RequestMapping(value = "/team/v1/_generate", method = RequestMethod.POST)
    public ResponseEntity<TeamCodeGenerationResponse> teamV1GeneratePost(@ApiParam(value = "Number of team codes to mint for the tenant.", required = true) @Valid @RequestBody TeamCodeGenerationRequest request) {
        List<String> teamCodes = teamCodeService.generateTeamCodes(request);
        TeamCodeGenerationResponse response = TeamCodeGenerationResponse.builder()
                .teamCodes(teamCodes)
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @RequestMapping(value = "/team/v1/_search", method = RequestMethod.POST)
    public ResponseEntity<TeamCodeSearchResponse> teamV1SearchPost(
            @Valid @ModelAttribute URLParams urlParams,
            @ApiParam(value = "Team code search criteria. Pass status=[UNASSIGNED] for the codes that are free to hand out.", required = true) @Valid @RequestBody TeamCodeSearchRequest request,
            @ApiParam(value = "Attach the membership trail - who holds the code, since when and who assigned it.", defaultValue = "false") @Valid @RequestParam(value = "includeMembers", required = false, defaultValue = "false") Boolean includeMembers) {
        SearchResponse<TeamCode> searchResponse = teamCodeService.searchTeamCodes(
                request.getTeamCodeSearch(),
                urlParams.getLimit(),
                urlParams.getOffset(),
                urlParams.getTenantId(),
                urlParams.getIncludeDeleted(),
                includeMembers
        );
        TeamCodeSearchResponse response = TeamCodeSearchResponse.builder()
                .teamCodes(searchResponse.getResponse())
                .totalCount(searchResponse.getTotalCount())
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @RequestMapping(value = "/team/v1/_update", method = RequestMethod.POST)
    public ResponseEntity<TeamAssignmentResponse> teamV1UpdatePost(@ApiParam(value = "Team code assignment for an Individual.", required = true) @Valid @RequestBody TeamAssignmentRequest request) {
        TeamAssignmentResult result = teamCodeService.updateTeamCode(request);
        TeamAssignmentResponse response = TeamAssignmentResponse.builder()
                .assignments(result.getAssignments())
                .teamCode(result.getTeamCode())
                .updatedCount(result.getUpdatedCount())
                .skippedCount(result.getSkippedCount())
                .responseInfo(ResponseInfoFactory
                        .createResponseInfo(request.getRequestInfo(), true))
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
