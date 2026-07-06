package org.egov.individual.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.individual.AddressType;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.user.CreateUserRequest;
import org.egov.common.models.user.UserRequest;
import org.egov.common.service.UserService;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.ServiceRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserIntegrationService {

    private static final Random RANDOM = new Random();

    private final UserService userService;

    private final IndividualProperties individualProperties;

    private final ServiceRequestRepository serviceRequestRepository;

    @Value("${egov.user.host}")
    private String userHost;

    @Value("${egov.bulk.create.user.url:/user/users/v2/_create}")
    private String bulkCreateUserUrl;

    @Autowired
    public UserIntegrationService(UserService userService,
                                  IndividualProperties individualProperties,
                                  ServiceRequestRepository serviceRequestRepository) {
        this.userService = userService;
        this.individualProperties = individualProperties;
        this.serviceRequestRepository = serviceRequestRepository;
    }

    public List<UserRequest> createUser(Individual validIndividual,
                                            RequestInfo requestInfo) {
        return createUser(validIndividual, requestInfo, true);
    }

    public List<UserRequest> createUser(Individual validIndividual,
                                            RequestInfo requestInfo,
                                            boolean generateDummyMobile) {
        log.info("integrating with user service");
        UserRequest userRequest = IndividualMapper.toUserRequest(validIndividual, individualProperties, generateDummyMobile);
        return userService.create(new CreateUserRequest(requestInfo, userRequest));
    }


    public List<UserRequest> updateUser(Individual validIndividual,
                                            RequestInfo requestInfo) {
        log.info("updating the user in user service");
        UserRequest userRequest = IndividualMapper.toUserRequest(validIndividual, individualProperties);
        return userService.update(new CreateUserRequest(requestInfo, userRequest));
    }

    public List<UserRequest> deleteUser(List<Individual> validIndividuals,
                                            RequestInfo requestInfo) {
        log.info("deleting the user in user service");
        List<UserRequest> userRequests = validIndividuals.stream()
                .filter(Individual::getIsSystemUser).map(toUserRequest())
                .peek(userRequest -> userRequest.setActive(Boolean.FALSE))
                .collect(Collectors.toList());
        return userRequests.stream().flatMap(userRequest -> userService.update(
                new CreateUserRequest(requestInfo,
                        userRequest)).stream()).collect(Collectors.toList());
    }

    /**
     * Bulk-create all system users for the given individuals in a single HTTP
     * call to egov-user's v2 endpoint. No loop — the whole batch goes on the
     * wire as one request.
     * <p>
     * Wire format is a raw HashMap (no BulkUserCreateRequest DTO). Each user is
     * built as a {@code Map<String, Object>} with lowercase field names
     * matching egov-user's domain model. The response is returned to the
     * caller as-is so it can correlate results by username.
     *
     * @return {@link BulkUserResult} — carries both the returned user maps
     *         (id populated on success, null on failure) AND the per-user
     *         error info from egov-user (code, message, username). Empty
     *         result on any unrecoverable transport failure — the outer
     *         caller can retrieve that from the throw path below.
     */
    public static class BulkUserResult {
        public final List<Map<String, Object>> users;
        public final List<Map<String, Object>> errors;
        public BulkUserResult(List<Map<String, Object>> users,
                              List<Map<String, Object>> errors) {
            this.users = users;
            this.errors = errors;
        }
    }

    @SuppressWarnings("unchecked")
    public BulkUserResult createUsersBulk(List<Individual> individuals,
                                          RequestInfo requestInfo,
                                          boolean generateDummyMobile) {
        if (individuals == null || individuals.isEmpty()) {
            return new BulkUserResult(Collections.emptyList(), Collections.emptyList());
        }
        log.info("bulk-integrating with user service for {} individuals", individuals.size());

        List<Map<String, Object>> users = individuals.stream()
                .map(i -> toUserMap(i, generateDummyMobile))
                .collect(Collectors.toList());

        Map<String, Object> body = new HashMap<>();
        body.put("RequestInfo", requestInfo);
        body.put("users", users);

        // Direct POST to egov-user's v2 bulk endpoint via Individual's own
        // ServiceRequestRepository — no coupling to a shared bulkCreate helper.
        StringBuilder uri = new StringBuilder(userHost).append(bulkCreateUserUrl);
        Object rawResponse = serviceRequestRepository.fetchResult(uri, body);
        if (!(rawResponse instanceof Map)) {
            return new BulkUserResult(Collections.emptyList(), Collections.emptyList());
        }
        Map<String, Object> respMap = (Map<String, Object>) rawResponse;
        List<Map<String, Object>> savedUsers = Collections.emptyList();
        List<Map<String, Object>> errors = Collections.emptyList();
        Object usersObj = respMap.get("users");
        if (usersObj instanceof List) {
            savedUsers = (List<Map<String, Object>>) usersObj;
        }
        Object errorsObj = respMap.get("errors");
        if (errorsObj instanceof List) {
            errors = (List<Map<String, Object>>) errorsObj;
        }
        return new BulkUserResult(savedUsers, errors);
    }

    /**
     * Build the wire representation of a single user as a HashMap. Field names
     * are lowercase to match egov-user's domain model (v2 endpoint deserialises
     * against {@code org.egov.user.domain.model.User}).
     */
    private Map<String, Object> toUserMap(Individual ind, boolean generateDummyMobile) {
        Map<String, Object> u = new HashMap<>();
        u.put("tenantId", ind.getTenantId());
        u.put("name", ind.getName().getFamilyName() != null
                ? String.join(" ", ind.getName().getGivenName(), ind.getName().getFamilyName())
                : ind.getName().getGivenName());
        u.put("username", ind.getUserDetails() != null && ind.getUserDetails().getUsername() != null
                ? ind.getUserDetails().getUsername()
                : UUID.randomUUID().toString());
        u.put("mobileNumber", generateDummyMobile
                ? generateDummyMobileNumber(ind.getMobileNumber())
                : ind.getMobileNumber());
        u.put("emailId", ind.getEmail());
        u.put("type", individualProperties.getUserServiceUserType());
        u.put("accountLocked", individualProperties.isUserServiceAccountLocked());
        u.put("active", ind.getIsSystemUserActive());
        if (ind.getUserDetails() != null && ind.getUserDetails().getPassword() != null) {
            u.put("password", ind.getUserDetails().getPassword());
        }
        if (ind.getAddress() != null && !ind.getAddress().isEmpty()) {
            ind.getAddress().stream()
                    .filter(a -> AddressType.CORRESPONDENCE.equals(a.getType()))
                    .findFirst()
                    .ifPresent(a -> {
                        // egov-user's domain User.correspondenceAddress is an
                        // Address object, not a String. Send it in the shape
                        // egov-user expects (address, type, tenantId).
                        Map<String, Object> addr = new HashMap<>();
                        addr.put("address", a.getAddressLine1());
                        addr.put("type", "CORRESPONDENCE");
                        addr.put("tenantId", ind.getTenantId());
                        u.put("correspondenceAddress", addr);
                    });
        }
        if (ind.getUserDetails() != null && ind.getUserDetails().getRoles() != null) {
            List<Map<String, Object>> roles = ind.getUserDetails().getRoles().stream()
                    .map(role -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("code", role.getCode());
                        m.put("name", role.getName());
                        m.put("tenantId", ind.getTenantId());
                        return m;
                    })
                    .collect(Collectors.toList());
            u.put("roles", roles);
        }
        return u;
    }

    private static String generateDummyMobileNumber(String mobileNumber) {
        if (mobileNumber == null) {
            int number = RANDOM.nextInt(900000000) + 100000000;
            return "1" + number;
        }
        return mobileNumber;
    }

    private Function<Individual, UserRequest> toUserRequest() {
        return individual -> IndividualMapper
                .toUserRequest(individual,
                        individualProperties);
    }
}
