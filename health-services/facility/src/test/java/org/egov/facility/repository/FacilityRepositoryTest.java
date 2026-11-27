package org.egov.facility.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.models.facility.FacilitySearch;
import org.egov.common.producer.Producer;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.facility.repository.rowmapper.FacilityRowMapper;
import org.egov.facility.service.OutsideHouseholdTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The paged facility search must have a fixed order, so that reading page after page neither repeats nor skips facilities. */
@ExtendWith(MockitoExtension.class)
class FacilityRepositoryTest {

    @Mock private Producer producer;
    @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private SelectQueryBuilder selectQueryBuilder;
    @Mock private FacilityRowMapper facilityRowMapper;
    @Mock private OutsideHouseholdTypeService outsideHouseholdTypeService;
    @Mock private MultiStateInstanceUtil multiStateInstanceUtil;

    private FacilityRepository facilityRepository;

    @BeforeEach
    void setUp() throws Exception {
        facilityRepository = new FacilityRepository(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                facilityRowMapper, outsideHouseholdTypeService);
        ReflectionTestUtils.setField(facilityRepository, "multiStateInstanceUtil", multiStateInstanceUtil);
        when(multiStateInstanceUtil.replaceSchemaPlaceholder(anyString(), anyString())).thenAnswer(i -> i.getArgument(0));
        when(outsideHouseholdTypeService.getTypes(anyString())).thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(ResultSetExtractor.class))).thenReturn(0L);
        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(Collections.emptyList());
    }

    private String pagedQuery() {
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate).query(query.capture(), anyMap(), any(RowMapper.class));
        return query.getValue();
    }

    private String countQuery() {
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate).query(query.capture(), anyMap(), any(ResultSetExtractor.class));
        return query.getValue();
    }

    @Test
    void pagesInFacilityIdOrder() throws Exception {
        FacilitySearch search = FacilitySearch.builder().isPermanent(true).build();

        facilityRepository.find(search, 50, 100, "mz", null, false);

        assertTrue(pagedQuery().endsWith(" ORDER BY f.id ASC LIMIT :limit OFFSET :offset"), pagedQuery());
    }

    @Test
    void pagesInFacilityIdOrderWhenSearchingBoundariesBelow() throws Exception {
        FacilitySearch search = FacilitySearch.builder().isPermanent(true).localityCode("ROOT").build();

        facilityRepository.find(search, 1000, 0, "mz", null, false, List.of("ROOT", "CHILD"));

        String query = pagedQuery();
        assertTrue(query.contains("a.localityCode = ANY(:localityCodes)"), query);
        assertTrue(query.endsWith(" ORDER BY f.id ASC LIMIT :limit OFFSET :offset"), query);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsTheBoundariesBelowAsOneArrayParameter() throws Exception {
        facilityRepository.find(FacilitySearch.builder().isPermanent(true).build(), 1000, 0, "mz", null, false, List.of("ROOT", "CHILD"));

        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(namedParameterJdbcTemplate).query(anyString(), params.capture(), any(RowMapper.class));
        assertArrayEquals(new String[]{"ROOT", "CHILD"}, (String[]) params.getValue().get("localityCodes"));
    }

    @Test
    void countsWithoutTheOrder() throws Exception {
        facilityRepository.find(FacilitySearch.builder().isPermanent(true).build(), 50, 0, "mz", null, false);

        assertFalse(countQuery().contains("ORDER BY"), countQuery());
    }
}
