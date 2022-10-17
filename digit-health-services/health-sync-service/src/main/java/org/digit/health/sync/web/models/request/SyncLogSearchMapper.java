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

    @Mapping(target = "fileDetails.fileStoreId", source = "fileStoreId")
    SyncLogData toData(SyncLogSearchDto syncLogSearchDto);

}
