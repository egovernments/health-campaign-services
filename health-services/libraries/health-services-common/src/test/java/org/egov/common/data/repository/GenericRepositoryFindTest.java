package org.egov.common.data.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.helpers.SomeObject;
import org.egov.common.helpers.SomeRepository;
import org.egov.common.helpers.SomeRowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenericRepositoryFindTest {
    @InjectMocks
    private SomeRepository someRepository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private SelectQueryBuilder selectQueryBuilder;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SomeRowMapper productVariantRowMapper;

    @Mock
    private HashOperations hashOperations;

    private List<String> someObjectIds;

    private List<SomeObject> someObjects;

    @BeforeEach
    void setUp() {
        someObjects = new ArrayList<>();
        someObjects.add(SomeObject.builder()
                .id("some-id")
                .otherField("other-field")
                .isDeleted(false)
                .build());
        someObjects.add(SomeObject.builder()
                .id("other-id")
                .isDeleted(true)
                .build());
        someObjectIds = someObjects.stream().map(SomeObject::getId)
                .collect(Collectors.toList());
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ReflectionTestUtils.setField(someRepository, "timeToLive", "60");
    }

    @Test
    @DisplayName("should find objects by ids and return the results")
    void shouldFindObjectsByIdsAndReturnTheResults() {
        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(someObjects);

        List<SomeObject> result = someRepository.findById(someObjectIds);

        assertEquals(someObjectIds.size(), result.size());
    }

    @Test
    @DisplayName("should return empty list if the record is not found in db and cache")
    void shouldReturnEmptyListIfRecordIsNotFoundInDbAndCache() {
        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(Collections.emptyList());
        when(namedParameterJdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        List<SomeObject> result = someRepository.findById(someObjectIds);

        assertEquals(result.size(), 0);
    }

    @Test
    @DisplayName("should get objects from db for the search request")
    void shouldReturnObjectsFromDBForSearchRequest() throws QueryBuilderException {
        List<SomeObject> result = new ArrayList<>(someObjects);
        List<SomeObject> deleted = result.stream().filter(someObject -> someObject.getIsDeleted() == Boolean.TRUE)
                .collect(Collectors.toList());
        result.removeAll(deleted);
        when(selectQueryBuilder.build(any(Object.class), anyString()))
                .thenReturn("Select * from some_table where id='some-id' and isdeleted=false");
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(SomeRowMapper.class)))
                .thenReturn(result);

        List<SomeObject> productVariantResponse = someRepository.find(someObjects.get(0),
                2, 0, "default", null, false);

        assertEquals(1, productVariantResponse.size());
    }

    @Test
    @DisplayName("get products from db which are deleted")
    void shouldReturnObjectsFromDBForSearchRequestWithDeletedIncluded() throws QueryBuilderException {
        when(selectQueryBuilder.build(any(Object.class), anyString()))
                .thenReturn("Select * from some_table where id='some-id' and otherfield='other-field'");
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(SomeRowMapper.class)))
                .thenReturn(someObjects);

        List<SomeObject> productVariantResponse = someRepository.find(someObjects.get(0),
                2, 0, "default", null, true);

        assertEquals(2, productVariantResponse.size());
    }

    @Test
    @DisplayName("should validate id using column name")
    void shouldReturnValidIdsFromDBOrCache() {
        when(hashOperations.multiGet(anyString(), anyList())).thenReturn(
                Arrays.asList(SomeObject.builder().id("id1").isDeleted(Boolean.FALSE).build()
                        ,SomeObject.builder().isDeleted(Boolean.FALSE).id("id2").build()));
        when(namedParameterJdbcTemplate.query(any(String.class), any(Map.class), any(SomeRowMapper.class)))
                .thenReturn(someObjects);
        List<String> idsToValidate = new ArrayList<>();
        idsToValidate.add("id1");
        idsToValidate.add("id2");
        idsToValidate.add("id3");
        idsToValidate.add("id4");
        List<String> idsFound = someRepository.validateIds(idsToValidate, "id");

        assertEquals(idsFound.size(), 4);
    }
}