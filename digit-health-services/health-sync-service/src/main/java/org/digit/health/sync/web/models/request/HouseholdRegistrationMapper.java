package org.digit.health.sync.web.models.request;

import org.digit.health.sync.web.models.HouseholdRegistration;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface HouseholdRegistrationMapper {
    HouseholdRegistrationMapper INSTANCE = Mappers.getMapper(HouseholdRegistrationMapper.class);

    HouseholdRegistrationRequest toRequest(HouseholdRegistration householdRegistration);
}
