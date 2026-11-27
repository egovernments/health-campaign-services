package org.egov.hrms.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.hrms.model.Employee;
import org.egov.hrms.web.contract.UserRequest;
import org.egov.hrms.web.contract.UserResponse;

import java.util.List;
import java.util.Map;

public interface UserService {

    UserResponse createUser(UserRequest userRequest);

    /**
     * Bulk-creates the users for the given employees in a single request. This works for both the
     * egov-user backend ({@link DefaultUserService}, POST /users/v2/_create) and the individual
     * backend ({@link IndividualService}, POST /individual/v1/bulk/_create?synchronous=true),
     * replacing the earlier per-employee create loop.
     *
     * The returned {@link UserResponse} contains the created users populated with username, uuid,
     * id (and userServiceUuid for the individual backend); callers correlate them back to the
     * originating employees by username.
     *
     * @param requestInfo the request info to forward to the backend
     * @param employees   the employees whose users (already enriched) are to be created
     * @return the created users
     */
    UserResponse createUsers(RequestInfo requestInfo, List<Employee> employees);

    UserResponse updateUser(UserRequest userRequest);

    UserResponse getUser(RequestInfo requestInfo, Map<String, Object> userSearchCriteria);

    /**
     * Bulk-searches existing users by a list of usernames within a tenant, restricted to the
     * EMPLOYEE user type. This is a single call that works for both the egov-user backend
     * ({@link DefaultUserService}) and the individual backend ({@link IndividualService}),
     * replacing the earlier per-employee username/mobile search loops in the validator.
     *
     * @param requestInfo the request info to forward to the backend
     * @param usernames   the list of usernames (employee codes) to look up
     * @param tenantId    the tenant to search within (mandatory for both backends)
     * @return a {@link UserResponse} whose user list contains every existing user whose
     *         username is one of the supplied usernames (empty when none match)
     */
    UserResponse searchByUsernames(RequestInfo requestInfo, List<String> usernames, String tenantId);
}
