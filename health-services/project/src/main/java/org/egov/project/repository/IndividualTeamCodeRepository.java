package org.egov.project.repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.IndividualSearchRequest;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.web.models.team.IndividualTeamCodeResponse;
import org.egov.project.web.models.team.IndividualTeamCodeSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import static org.egov.project.Constants.INDIVIDUAL_SERVICE_UNAVAILABLE;
import static org.egov.project.Constants.TEAM_CODE_ASSIGNED_STATUS;
import static org.egov.project.Constants.TEAM_CODE_INACTIVE_STATUS;
import static org.egov.project.Constants.TEAM_CODE_INACTIVE;
import static org.egov.project.Constants.TEAM_CODE_NOT_FOUND;

/**
 * Reads team codes and their membership from the individual service, which owns the team code
 * registry.
 *
 * Membership is read from /individual/team/v1/_search rather than from each individual's
 * additionalFields, because the individual row is written through Kafka and lags egov-persister
 * while the individual service's mapping table is written synchronously. Reading the mapping table
 * means staff can be assigned to a team and their beneficiaries attributed in the same minute.
 */
@Repository
@Slf4j
public class IndividualTeamCodeRepository {

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    public IndividualTeamCodeRepository(ServiceRequestClient serviceRequestClient,
                                        ProjectConfiguration projectConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.projectConfiguration = projectConfiguration;
    }

    /**
     * Confirms the team code exists and is not retired, and returns the userUuids of its current
     * members.
     *
     * @throws CustomException TEAM_CODE_NOT_FOUND when it was never minted, TEAM_CODE_INACTIVE
     *                         when it has been retired
     */
    public Set<String> fetchAssignableTeamMemberUserUuids(String tenantId, String teamCode,
                                                          RequestInfo requestInfo) {
        IndividualTeamCodeSearchRequest request = IndividualTeamCodeSearchRequest.builder()
                .requestInfo(requestInfo)
                .teamCodeSearch(IndividualTeamCodeSearchRequest.TeamCodeSearch.builder()
                        .teamCode(Collections.singletonList(teamCode))
                        .build())
                .build();

        StringBuilder uri = new StringBuilder(projectConfiguration.getIndividualServiceHost()
                + projectConfiguration.getIndividualTeamSearchUrl()
                + "?tenantId=" + tenantId
                + "&limit=1&offset=0&includeMembers=true");

        IndividualTeamCodeResponse response;
        try {
            response = serviceRequestClient.fetchResult(uri, request, IndividualTeamCodeResponse.class);
        } catch (Exception exception) {
            log.error("error while searching team code {} in the individual service", teamCode, exception);
            throw new CustomException(INDIVIDUAL_SERVICE_UNAVAILABLE,
                    "could not reach the individual service to validate team code " + teamCode);
        }

        if (response == null || CollectionUtils.isEmpty(response.getTeamCodes())) {
            throw new CustomException(TEAM_CODE_NOT_FOUND, teamCode);
        }
        IndividualTeamCodeResponse.IndividualTeamCode found = response.getTeamCodes().get(0);
        if (TEAM_CODE_INACTIVE_STATUS.equals(found.getStatus())) {
            throw new CustomException(TEAM_CODE_INACTIVE, teamCode);
        }
        return Optional.ofNullable(found.getMembers()).orElse(Collections.emptyList()).stream()
                .filter(member -> TEAM_CODE_ASSIGNED_STATUS.equals(member.getStatus()))
                .map(IndividualTeamCodeResponse.IndividualTeamMember::getUserUuid)
                .filter(uuid -> uuid != null && !uuid.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * Confirms every one of these team codes exists and is not retired, in a single call.
     *
     * @throws CustomException TEAM_CODE_NOT_FOUND naming the codes that were never minted,
     *                         TEAM_CODE_INACTIVE naming the ones that have been retired
     */
    public void validateTeamCodesAssignable(String tenantId, List<String> teamCodes, RequestInfo requestInfo) {
        if (CollectionUtils.isEmpty(teamCodes)) {
            return;
        }
        IndividualTeamCodeSearchRequest request = IndividualTeamCodeSearchRequest.builder()
                .requestInfo(requestInfo)
                .teamCodeSearch(IndividualTeamCodeSearchRequest.TeamCodeSearch.builder()
                        .teamCode(teamCodes)
                        .build())
                .build();

        StringBuilder uri = new StringBuilder(projectConfiguration.getIndividualServiceHost()
                + projectConfiguration.getIndividualTeamSearchUrl()
                + "?tenantId=" + tenantId
                + "&limit=" + teamCodes.size() + "&offset=0");

        IndividualTeamCodeResponse response;
        try {
            response = serviceRequestClient.fetchResult(uri, request, IndividualTeamCodeResponse.class);
        } catch (Exception exception) {
            log.error("error while validating team codes {} in the individual service", teamCodes, exception);
            throw new CustomException(INDIVIDUAL_SERVICE_UNAVAILABLE,
                    "could not reach the individual service to validate team codes " + teamCodes);
        }

        List<IndividualTeamCodeResponse.IndividualTeamCode> found =
                response == null || response.getTeamCodes() == null ? Collections.emptyList()
                        : response.getTeamCodes();
        Set<String> foundCodes = found.stream()
                .map(IndividualTeamCodeResponse.IndividualTeamCode::getTeamCode)
                .collect(Collectors.toSet());
        List<String> missing = teamCodes.stream().filter(code -> !foundCodes.contains(code)).toList();
        if (!missing.isEmpty()) {
            throw new CustomException(TEAM_CODE_NOT_FOUND, String.valueOf(missing));
        }
        List<String> inactive = found.stream()
                .filter(code -> TEAM_CODE_INACTIVE_STATUS.equals(code.getStatus()))
                .map(IndividualTeamCodeResponse.IndividualTeamCode::getTeamCode)
                .toList();
        if (!inactive.isEmpty()) {
            throw new CustomException(TEAM_CODE_INACTIVE, String.valueOf(inactive));
        }
    }

    /**
     * Resolves usernames to the egov-user uuid held in project_beneficiary.createdBy.
     *
     * @return username -> userUuid, omitting any username that did not resolve so the caller can
     *         report exactly which ones were unknown
     */
    public Map<String, String> resolveUsernamesToUserUuids(String tenantId, List<String> usernames,
                                                           RequestInfo requestInfo) {
        if (CollectionUtils.isEmpty(usernames)) {
            return Collections.emptyMap();
        }
        IndividualSearchRequest request = IndividualSearchRequest.builder()
                .requestInfo(requestInfo)
                .individual(IndividualSearch.builder().username(usernames).build())
                .build();

        StringBuilder uri = new StringBuilder(projectConfiguration.getIndividualServiceHost()
                + projectConfiguration.getIndividualServiceSearchUrl()
                + "?tenantId=" + tenantId
                + "&limit=" + usernames.size()
                + "&offset=0&includeDeleted=false");

        IndividualBulkResponse response;
        try {
            response = serviceRequestClient.fetchResult(uri, request, IndividualBulkResponse.class);
        } catch (Exception exception) {
            log.error("error while resolving usernames in the individual service", exception);
            throw new CustomException(INDIVIDUAL_SERVICE_UNAVAILABLE,
                    "could not reach the individual service to resolve usernames");
        }

        Map<String, String> userUuidByUsername = new LinkedHashMap<>();
        if (response == null || CollectionUtils.isEmpty(response.getIndividual())) {
            return userUuidByUsername;
        }
        for (Individual individual : response.getIndividual()) {
            if (individual.getUserDetails() == null || individual.getUserDetails().getUsername() == null
                    || individual.getUserUuid() == null) {
                continue;
            }
            userUuidByUsername.put(individual.getUserDetails().getUsername(), individual.getUserUuid());
        }
        return userUuidByUsername;
    }
}
