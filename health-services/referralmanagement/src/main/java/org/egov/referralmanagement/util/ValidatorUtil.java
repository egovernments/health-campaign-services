package org.egov.referralmanagement.util;

import digit.models.coremodels.UserSearchRequest;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.service.UserService;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A Validation helper Util
 */
public class ValidatorUtil {

    /**
     * validate and remove valid identifiers from invalidStaffIds
     * @param requestInfo
     * @param userService
     * @param staffIds
     * @param invalidStaffIds
     */
    public static void validateAndEnrichStaffIds(RequestInfo requestInfo, UserService userService,
                                                  List<String> staffIds, List<String> invalidStaffIds) {
        if (!CollectionUtils.isEmpty(staffIds)) {
            UserSearchRequest userSearchRequest = new UserSearchRequest();
            userSearchRequest.setRequestInfo(requestInfo);
            userSearchRequest.setUuid(staffIds);
            List<String> validStaffIds = userService.search(userSearchRequest).stream().map(user -> user.getUuid())
                    .collect(Collectors.toList());
            invalidStaffIds.removeAll(validStaffIds);
        }
    }
}
