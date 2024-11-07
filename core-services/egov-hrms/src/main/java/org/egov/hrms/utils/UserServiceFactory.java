package org.egov.hrms.utils;

import org.egov.hrms.service.DefaultUserService;
import org.egov.hrms.service.IndividualService;
import org.egov.hrms.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserServiceFactory {


    @Autowired
    private IndividualService individualService;

    @Autowired
    private DefaultUserService defaultUserService;

    public UserService getUserService(String qualifier) {
        if ("individualService".equalsIgnoreCase(qualifier)) {
            return individualService;
        } else if ("defaultUserService".equalsIgnoreCase(qualifier)) {
            return defaultUserService;
        } else {
            return null;
        }
    }
}

