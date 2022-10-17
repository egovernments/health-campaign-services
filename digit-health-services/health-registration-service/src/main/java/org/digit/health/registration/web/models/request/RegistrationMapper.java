package org.digit.health.registration.web.models.request;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface RegistrationMapper {
    RegistrationMapper INSTANCE = Mappers.getMapper(RegistrationMapper.class);

    RegistrationRequest toEntity(RegistrationDto registrationDto);

    RegistrationDto toDTO(RegistrationRequest registrationRequest);

}
