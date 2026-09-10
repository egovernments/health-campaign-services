package org.egov.id.repository;

import org.egov.common.models.idgen.IdRecord;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.id.config.PropertiesManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private IdRecordRowMapper idRecordRowMapper;

    @Mock
    private IdTransactionLogRowMapper idTransactionLogRowMapper;

    @Mock
    private PropertiesManager propertiesManager;

    private static final String CENTRAL_TENANT_ID = "bednet.city.a";
    private static final String SINGLE_TENANT_ID = "bednet";

    private IdRepository buildRepository(MultiStateInstanceUtil multiStateInstanceUtil) {
        return new IdRepository(jdbcTemplate, namedParameterJdbcTemplate, idRecordRowMapper,
                idTransactionLogRowMapper, propertiesManager, multiStateInstanceUtil);
    }

    @Test
    void shouldQualifyQueryWithSchemaWhenCentralInstance() throws Exception {
        IdRepository idRepository = buildRepository(new MultiStateInstanceUtil(1, true, 0));
        when(namedParameterJdbcTemplate.query(any(String.class), anyMap(), eq(idRecordRowMapper)))
                .thenReturn(Collections.singletonList(IdRecord.builder().id("X").build()));

        idRepository.findByIDsAndStatus(List.of("X"), null, CENTRAL_TENANT_ID);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(namedParameterJdbcTemplate).query(queryCaptor.capture(), anyMap(), eq(idRecordRowMapper));
        String query = queryCaptor.getValue();
        assertTrue(query.contains("bednet.id_pool"));
        assertFalse(query.contains("{schema}"));
    }

    @Test
    void shouldQualifyFetchUnassignedQueryWithSchemaWhenCentralInstance() throws Exception {
        IdRepository idRepository = buildRepository(new MultiStateInstanceUtil(1, true, 0));
        when(namedParameterJdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class), eq(idRecordRowMapper)))
                .thenReturn(Collections.singletonList(IdRecord.builder().id("X").build()));

        idRepository.fetchUnassigned(CENTRAL_TENANT_ID, "user-1", 5);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(namedParameterJdbcTemplate).query(queryCaptor.capture(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class), eq(idRecordRowMapper));
        String query = queryCaptor.getValue();
        assertTrue(query.contains("bednet.id_pool"));
        assertFalse(query.contains("{schema}"));
    }

    @Test
    void shouldLeaveQueryUnqualifiedWhenSingleInstance() throws Exception {
        IdRepository idRepository = buildRepository(new MultiStateInstanceUtil(1, false, 0));
        when(namedParameterJdbcTemplate.query(any(String.class), anyMap(), eq(idRecordRowMapper)))
                .thenReturn(Collections.singletonList(IdRecord.builder().id("X").build()));

        idRepository.findByIDsAndStatus(List.of("X"), null, SINGLE_TENANT_ID);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(namedParameterJdbcTemplate).query(queryCaptor.capture(), anyMap(), eq(idRecordRowMapper));
        String query = queryCaptor.getValue();
        assertTrue(query.contains("FROM id_pool"));
        assertFalse(query.contains("{schema}"));
    }

    @Test
    void shouldQualifyCountQueryWithSchemaWhenCentralInstance() throws Exception {
        IdRepository idRepository = buildRepository(new MultiStateInstanceUtil(1, true, 0));
        when(namedParameterJdbcTemplate.queryForObject(any(String.class), anyMap(), eq(Long.class)))
                .thenReturn(5L);

        long count = idRepository.selectIDsForUserDeviceCount(CENTRAL_TENANT_ID, null, null, null, 10, 0, false);

        assertEquals(5L, count);
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(namedParameterJdbcTemplate).queryForObject(queryCaptor.capture(), anyMap(), eq(Long.class));
        assertTrue(queryCaptor.getValue().contains("bednet.id_transaction_log"));
    }

}
