package org.digit.health.sync.repository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultQueryBuilderTest {

    @Test
    @DisplayName("should build a query based on data object and its primitive properties")
    void shouldBuildAQueryBasedOnDataObjectAndItsPrimitiveProperties() {
        DummyData data = DummyData.builder()
                .dummyString("some-string")
                .dummyInt(1)
                .build();
        String expectedQuery = "SELECT * FROM dummyData WHERE " +
                "dummyString:=dummyString AND dummyInt:=dummyInt";
        QueryBuilder queryBuilder = new DefaultQueryBuilder();

        String actualQuery = queryBuilder.buildSelectQuery(data);

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    @DisplayName("should build a query based on data object and its primitive properties")
    void shouldBuildAQueryBasedOnDataObjectAndItsPrimitiveProperties2() {
        DummyData data = DummyData.builder()
                .dummyString("some-string")
                .dummyInt(1)
                .build();
        String expectedQuery = "SELECT * FROM dummyData WHERE " +
                "dummyString:=dummyString AND dummyInt:=dummyInt";
        QueryBuilder queryBuilder = new DefaultQueryBuilder();

        String actualQuery = queryBuilder.buildSelectQuery(data);

        assertEquals(expectedQuery, actualQuery);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Table(name = "dummyData")
    static class DummyData {
        private String dummyString;
        private Integer dummyInt;
        private Boolean dummyBoolean;
        private Float dummyFloat;
        private Double dummyDouble;

        private int dummyPrimitiveInt;
        private boolean dummyPrimitiveBoolean;
        private float dummyPrimitiveFloat;
        private double dummyPrimitiveDouble;

        private DummyAddress dummyAddress;
        private DummyAmount dummyAmount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Table(name = "dummyAddress")
    static class DummyAddress {
        private String addressString;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Table(name = "dummyAmount")
    static class DummyAmount {
        private String currency;
        private double amount;
    }
}
