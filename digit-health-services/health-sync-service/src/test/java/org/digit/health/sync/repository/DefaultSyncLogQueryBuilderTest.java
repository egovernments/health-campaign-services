package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.ReferenceId;
import org.digit.health.sync.web.models.SyncStatus;
import org.digit.health.sync.web.models.dao.SyncLogData;
import org.digit.health.sync.web.models.request.SyncLogSearchDto;
import org.digit.health.sync.web.models.request.SyncLogSearchMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class DefaultSyncLogQueryBuilderTest {

    @InjectMocks
    private DefaultSyncLogQueryBuilder defaultSyncLogQueryBuilder;

    @DisplayName("should generate query with sync id and tenant id if request have sync id")
    @Test
    void shouldGenerateQueryWithSyncId() {
        SyncLogData syncLogData = SyncLogSearchMapper.INSTANCE.toData(
                SyncLogSearchDto.builder().syncId("sync-id").build()
        );
        assertEquals(
                "SELECT * FROM sync_log  WHERE tenantId = :tenantId AND id=:id ",
                defaultSyncLogQueryBuilder.createSelectQuery(syncLogData)
        );
    }


    @DisplayName("should generate query with reference id and type and tenant id if request have reference")
    @Test
    void shouldGenerateQueryForReferenceId() {
        SyncLogData syncLogData = SyncLogSearchMapper.INSTANCE.toData(
                SyncLogSearchDto.builder().referenceId(ReferenceId.builder()
                                .type("campaign")
                                .id("id")
                                .build())
                        .build()
        );
        assertEquals("" +
                        "SELECT * FROM sync_log  WHERE tenantId = :tenantId AND referenceId=:referenceId AND referenceIdType=:referenceIdType ",
                defaultSyncLogQueryBuilder.createSelectQuery(syncLogData)
        );
    }

    @DisplayName("should generate query with file store id and tenant id if request have filestoreid")
    @Test
    void shouldGenerateQueryForFileStoreId() {
        SyncLogData syncLogData = SyncLogSearchMapper.INSTANCE.toData(
                SyncLogSearchDto.builder().fileStoreId("file-store-id")
                        .build()
        );
        assertEquals(
                "SELECT * FROM sync_log  WHERE tenantId = :tenantId AND fileStoreId=:fileStoreId ",
                defaultSyncLogQueryBuilder.createSelectQuery(syncLogData)
        );
    }


    @DisplayName("should generate query with status and tenant id if request have status")
    @Test
    void shouldGenerateQueryForStatus() {
        SyncLogData syncLogData = SyncLogSearchMapper.INSTANCE.toData(
                SyncLogSearchDto.builder().status(SyncStatus.CREATED.name())
                        .build()
        );
        assertEquals(
                "SELECT * FROM sync_log  WHERE tenantId = :tenantId AND status=:status ",
                defaultSyncLogQueryBuilder.createSelectQuery(syncLogData)
        );
    }


    @DisplayName("should generate query with status and reference and tenant id if request have status and reference")
    @Test
    void shouldGenerateQueryForStatusAndReference() {
        SyncLogData syncLogData = SyncLogSearchMapper.INSTANCE.toData(
                SyncLogSearchDto.builder()
                        .status(SyncStatus.CREATED.name())
                        .referenceId(ReferenceId.builder()
                                .type("campaign")
                                .id("id")
                                .build())
                        .build()
        );
        assertEquals(
                "SELECT * FROM sync_log  WHERE tenantId = :tenantId AND status=:status  AND referenceId=:referenceId AND referenceIdType=:referenceIdType ",
                defaultSyncLogQueryBuilder.createSelectQuery(syncLogData)
        );
    }


    @DisplayName("should generate update query for updating status for a sync Id")
    @Test
    void shouldGenerateUpdateQueryForUpdatingStatusForASyncIc() {
        SyncLogData syncLogData = SyncLogData.builder().syncId("syncId")
                .status(SyncStatus.CREATED)
                .build();
        assertEquals(
                "UPDATE sync_log SET  status = :status WHERE tenantId = :tenantId AND id=:id",
                defaultSyncLogQueryBuilder.createUpdateQuery(syncLogData)
        );
    }


}