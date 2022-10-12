package org.digit.health.sync.repository;

import org.digit.health.sync.helper.SyncSearchRequestTestBuilder;
import org.digit.health.sync.web.models.request.SyncSearchDto;
import org.digit.health.sync.web.models.request.SyncSearchMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SyncQueryBuilderTest {

    @InjectMocks
    private SyncQueryBuilder syncQueryBuilder;

    @DisplayName("should generate query with sync id and tenant id if request have sync id")
    @Test
    void shouldGenerateQueryWithSyncId() {
        SyncSearchDto searchDto = SyncSearchMapper.INSTANCE.toDTO(SyncSearchRequestTestBuilder.builder().withSyncId().build());
        assertEquals(
                "SELECT * FROM sync_log  WHERE tenantId='mq' AND id='sync-id'",
                syncQueryBuilder.getSQlBasedOn(searchDto)
        );
    }

    @DisplayName("should generate query with reference id and type and tenant id if request have reference")
    @Test
    void shouldGenerateQueryForReferenceId() {
        SyncSearchDto searchDto = SyncSearchMapper.INSTANCE.toDTO(SyncSearchRequestTestBuilder.builder().withReferenceId().build());
        assertEquals("" +
                "SELECT * FROM sync_log  WHERE tenantId='mq' AND referenceId='ref-id' AND referenceIdType='campaign'",
                syncQueryBuilder.getSQlBasedOn(searchDto)
        );
    }

    @DisplayName("should generate query with file store id and tenant id if request have filestoreid")
    @Test
    void shouldGenerateQueryForFileStoreId() {
        SyncSearchDto searchDto = SyncSearchMapper.INSTANCE.toDTO(SyncSearchRequestTestBuilder.builder().withFileStoreId().build());
        assertEquals(
                "SELECT * FROM sync_log  WHERE tenantId='mq' AND fileStoreId='file-store-id'",
                syncQueryBuilder.getSQlBasedOn(searchDto)
        );
    }

    @DisplayName("should generate query with status and tenant id if request have status")
    @Test
    void shouldGenerateQueryForStatus() {
        SyncSearchDto searchDto = SyncSearchMapper.INSTANCE.toDTO(SyncSearchRequestTestBuilder.builder().withStatus().build());
        assertEquals(
                "SELECT * FROM sync_log  WHERE tenantId='mq' AND status='FAILED'",
                syncQueryBuilder.getSQlBasedOn(searchDto)
        );
    }

    @DisplayName("should generate query with status and reference and tenant id if request have status and reference")
    @Test
    void shouldGenerateQueryForStatusAndReference() {
        SyncSearchDto searchDto = SyncSearchMapper.INSTANCE.toDTO(SyncSearchRequestTestBuilder.builder().withStatus().withReferenceId().build());
        assertEquals(
                "SELECT * FROM sync_log  WHERE tenantId='mq' AND status='FAILED' AND referenceId='ref-id' AND referenceIdType='campaign'",
                syncQueryBuilder.getSQlBasedOn(searchDto)
        );
    }
}