package org.digit.health.registration.web.models.request;

import javax.annotation.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2022-10-12T16:00:30+0530",
    comments = "version: 1.5.2.Final, compiler: javac, environment: Java 16.0.1 (AdoptOpenJDK)"
)
public class RegistrationMapperImpl implements RegistrationMapper {

    @Override
    public RegistrationRequest toEntity(RegistrationDto registrationDto) {
        if ( registrationDto == null ) {
            return null;
        }

        RegistrationRequest.RegistrationRequestBuilder registrationRequest = RegistrationRequest.builder();

        registrationRequest.requestInfo( registrationDto.getRequestInfo() );
        registrationRequest.registration( registrationDto.getRegistration() );

        return registrationRequest.build();
    }

    @Override
    public RegistrationDto toDTO(RegistrationRequest registrationRequest) {
        if ( registrationRequest == null ) {
            return null;
        }

        RegistrationDto.RegistrationDtoBuilder registrationDto = RegistrationDto.builder();

        registrationDto.requestInfo( registrationRequest.getRequestInfo() );
        registrationDto.registration( registrationRequest.getRegistration() );

        return registrationDto.build();
    }
}
