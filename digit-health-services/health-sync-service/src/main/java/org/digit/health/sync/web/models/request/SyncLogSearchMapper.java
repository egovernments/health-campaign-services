package org.digit.health.sync.web.models.request;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SyncLogSearchMapper {
    SyncLogSearchMapper INSTANCE = Mappers.getMapper(SyncLogSearchMapper.class);

    SyncLogSearchRequest toEntity(SyncLogSearchDto syncLogSearchDto);

    SyncLogSearchDto toDTO(SyncLogSearchRequest syncLogSearchRequest);

}
