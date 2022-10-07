package org.digit.health.sync.web.models.request;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SyncUpMapper {
    SyncUpMapper INSTANCE = Mappers.getMapper(SyncUpMapper.class);

    SyncUpRequest toEntity(SyncUpDto syncUpDto);

    SyncUpDto toDTO(SyncUpRequest syncUpRequest);

}
