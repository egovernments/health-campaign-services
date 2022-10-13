package org.digit.health.sync.repository;

import org.digit.health.sync.helper.SyncSearchRequestTestBuilder;
import org.digit.health.sync.web.models.request.SyncLogSearchDto;
import org.digit.health.sync.web.models.request.SyncLogSearchMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DefaultSyncLogQueryBuilderTest {

    @InjectMocks
    private DefaultSyncLogQueryBuilder defaultSyncLogQueryBuilder;

    @DisplayName("should generate query with sync id and tenant id if request have sync id")
    @Test
    void shouldGenerateQueryWithSyncId() {
        SyncLogSearchDto searchDto = SyncLogSearchMapper.INSTANCE.toDTO(SyncSearchRequestTestBuilder.builder().withSyncId().build());
        assertEquals(
                "SELECT * FROM sync_log  WHERE tenantId = :tenantId AND id=:id ",
                defaultSyncLogQueryBuilder.getSQlBasedOn(searchDto)
        );
    }

    @DisplayName("should generate query with reference id and type and tenant id if request have reference")
    @Test
    void shouldGenerateQueryForReferenceId() {
        SyncLogSearchDto searchDto = SyncLogSearchMapper.INSTANCE.toDTO(SyncSearchRequestTestBuilder.builder().withReferenceId().build());
        assertEquals("" +
                "SELECT * FROM sync_log  WHERE tenantId = :tenantId AND referenceId=:referenceId AND referenceIdType=:referenceIdType ",
                defaultSyncLogQueryBuilder.getSQlBasedOn(searchDto)
        );
    }

    @DisplayName("should generate query with file store id and tenant id if request have filestoreid")
    @Test
    void shouldGenerateQueryForFileStoreId() {
        SyncLogSearchDto searchDto = SyncLogSearchMapper.INSTANCE.toDTO(SyncSearchRequestTestBuilder.builder().withFileStoreId().build());
        assertEquals(
                "SELECT * FROM sync_log  WHERE tenantId = :tenantId AND fileStoreId=:fileStoreId ",
                defaultSyncLogQueryBuilder.getSQlBasedOn(searchDto)
        );
    }

    @DisplayName("should generate query with status and tenant id if request have status")
    @Test
    void shouldGenerateQueryForStatus() {
        SyncLogSearchDto searchDto = SyncLogSearchMapper.INSTANCE.toDTO(SyncSearchRequestTestBuilder.builder().withStatus().build());
        assertEquals(
                "SELECT * FROM sync_log  WHERE tenantId = :tenantId AND status=:status ",
                defaultSyncLogQueryBuilder.getSQlBasedOn(searchDto)
        );
    }

    @DisplayName("should generate query with status and reference and tenant id if request have status and reference")
    @Test
    void shouldGenerateQueryForStatusAndReference() {
        SyncLogSearchDto searchDto = SyncLogSearchMapper.INSTANCE.toDTO(SyncSearchRequestTestBuilder.builder().withStatus().withReferenceId().build());
        assertEquals(
                "SELECT * FROM sync_log  WHERE tenantId = :tenantId AND status=:status  AND referenceId=:referenceId AND referenceIdType=:referenceIdType ",
                defaultSyncLogQueryBuilder.getSQlBasedOn(searchDto)
        );
    }
}