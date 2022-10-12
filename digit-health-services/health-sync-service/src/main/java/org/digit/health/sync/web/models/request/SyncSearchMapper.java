package org.digit.health.sync.web.models.request;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SyncSearchMapper {
    SyncSearchMapper INSTANCE = Mappers.getMapper(SyncSearchMapper.class);

    SyncSearchRequest toEntity(SyncSearchDto syncSearchDto);

    SyncSearchDto toDTO(SyncSearchRequest syncSearchRequest);

}
