package org.digit.health.sync.web.models.dao;

import org.digit.health.sync.web.models.SyncLogStatus;
import org.digit.health.sync.web.models.AuditDetails;
import org.digit.health.sync.web.models.FileDetails;
import org.digit.health.sync.web.models.ReferenceId;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SyncLogDataMapper implements RowMapper<SyncLogData> {

    @Override
    public SyncLogData mapRow(ResultSet resultSet, int i) throws SQLException {
        return SyncLogData.builder()
                .referenceId(
                        ReferenceId.builder()
                                .id(resultSet.getString("referenceId"))
                                .type(resultSet.getString("referenceIdType"))
                                .build()
                )
                .auditDetails(
                        AuditDetails.builder()
                                .createdBy(resultSet.getString("createdBy"))
                                .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                                .createdTime(resultSet.getLong("createdTime"))
                                .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                                .build()
                )
                .tenantId(resultSet.getString("tenantId"))
                .status(SyncLogStatus.valueOf(resultSet.getString("status")))
                .comment(resultSet.getString("comment"))
                .syncId(resultSet.getString("id"))
                .successCount(resultSet.getLong("successCount"))
                .errorCount(resultSet.getLong("errorCount"))
                .totalCount(resultSet.getLong("totalCount"))
                .fileDetails(
                        FileDetails.builder()
                                .fileStoreId(resultSet.getString("fileStoreId"))
                                .checksum(resultSet.getString("checksum"))
                                .build()
                )
                .build();
    }
}
