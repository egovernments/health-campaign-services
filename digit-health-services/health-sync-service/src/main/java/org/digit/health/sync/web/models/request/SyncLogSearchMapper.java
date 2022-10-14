package org.digit.health.sync.web.models.request;

import org.digit.health.sync.web.models.dao.SyncLogData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SyncLogSearchMapper {
    SyncLogSearchMapper INSTANCE = Mappers.getMapper(SyncLogSearchMapper.class);

    SyncLogSearchRequest toEntity(SyncLogSearchDto syncLogSearchDto);

    SyncLogSearchDto toDTO(SyncLogSearchRequest syncLogSearchRequest);

    @Mapping(target="id", source="syncId")
    @Mapping(target="referenceId", source="reference.id")
    @Mapping(target="referenceIdType", source="reference.type")
    SyncLogData toData(SyncLogSearchDto syncLogSearchDto);

}
