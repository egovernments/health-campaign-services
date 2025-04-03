package org.egov.id.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.id.model.IdRecord;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class IdRecordRowMapper implements ResultSetExtractor<List<IdRecord>> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<IdRecord> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String, IdRecord> idRecordMap = new LinkedHashMap<>();

        while (rs.next()) {
            String id = rs.getString("id");
            String status = rs.getString("status");
            Timestamp createdAt = rs.getTimestamp("created_at");

            IdRecord record = new IdRecord();
            record.setId(id);
            record.setStatus(status);
            record.setCreatedAt(createdAt);

            if (!idRecordMap.containsKey(id)) {
                idRecordMap.put(id, record);
            }
        }

        return new ArrayList<>(idRecordMap.values());
    }
}
