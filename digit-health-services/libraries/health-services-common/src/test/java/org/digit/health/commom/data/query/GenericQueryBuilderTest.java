package org.digit.health.commom.data.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.digit.health.common.data.query.annotations.Table;
import org.digit.health.common.data.query.annotations.UpdateBy;
import org.digit.health.common.data.query.builder.SelectQueryBuilder;
import org.digit.health.common.data.query.builder.UpdateQueryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GenericQueryBuilderTest {

    @Test
    @DisplayName("should build a select query based on data object and its primitive properties")
    void shouldBuildSelectQueryBasedOnDataObjectAndItsPrimitiveProperties() {
        DummyData data = DummyData.builder()
                .dummyString("some-string")
                .dummyInt(1)
                .build();
        String expectedQuery = "SELECT * FROM dummyData WHERE " +
                "dummyString:=dummyString AND dummyInt:=dummyInt";
        SelectQueryBuilder queryBuilder = new SelectQueryBuilder();

        String actualQuery = queryBuilder.build(data);

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    @DisplayName("should not use primitive data types while building selecting query")
    void shouldNotUsePrimitiveDataTypesWhileBuildingSelectingQuery() {
        DummyData data = DummyData.builder()
                .dummyString("some-string")
                .dummyInt(1)
                .dummyPrimitiveBoolean(false)
                .dummyPrimitiveDouble(12.23)
                .dummyPrimitiveInt(12)
                .dummyPrimitiveFloat(232.2f)
                .build();
        String expectedQuery = "SELECT * FROM dummyData WHERE " +
                "dummyString:=dummyString AND dummyInt:=dummyInt";
        SelectQueryBuilder queryBuilder = new SelectQueryBuilder();

        String actualQuery = queryBuilder.build(data);

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    @DisplayName("should not use where clause when properties are set to null while building select query")
    void shouldNotUseWhereClauseWhenPropertiesAreSetToNullSelectQuery() {
        DummyData data = DummyData.builder()
                .build();
        String expectedQuery = "SELECT * FROM dummyData";
        SelectQueryBuilder queryBuilder = new SelectQueryBuilder();

        String actualQuery = queryBuilder.build(data);

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    @DisplayName("should use properties from nested object to build a select query")
    void shouldUsePropertiesFromNestedObjectToBuildSelectQuery() {
        DummyData data = DummyData.builder()
                .dummyString("TEST123")
                .dummyAddress(DummyAddress.builder().addressString("123").build())
                .build();
        String expectedQuery = "SELECT * FROM dummyData WHERE dummyString:=dummyString AND addressString:=addressString";
        SelectQueryBuilder queryBuilder = new SelectQueryBuilder();

        String actualQuery = queryBuilder.build(data);

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    @DisplayName("should use properties from nested object's nested object to build a select query")
    void shouldUsePropertiesFrom2LevelNestedObjectToBuildSelectQuery() {
        DummyData data = DummyData.builder()
                .dummyString("TEST123")
                .dummyAddress(DummyAddress
                        .builder()
                        .addressString("123")
                        .dummyAmount(DummyAmount.builder().amount(123.0).currency("INR").build()).build())
                .build();
        String expectedQuery = "SELECT * FROM dummyData WHERE dummyString:=dummyString AND addressString:=addressString AND currency:=currency AND amount:=amount";
        SelectQueryBuilder queryBuilder = new SelectQueryBuilder();

        String actualQuery = queryBuilder.build(data);

        assertEquals(expectedQuery, actualQuery);
    }

    @Test
    @DisplayName("Should use @updateBy to set the where clause")
    void shouldUseUpdateByAnnotationToSetTheWhereClause(){
        DummyData data = DummyData.builder()
                .dummyString("some-string")
                .dummyInt(1)
                .dummyAddress(DummyAddress
                        .builder()
                        .addressString("123").build())
                .build();
        String expectedQuery = "UPDATE dummyData SET dummyString:=dummyString , dummyInt:=dummyInt , addressString:=addressString WHERE dummyID:=dummyID";
        UpdateQueryBuilder queryBuilder = new UpdateQueryBuilder();

        String actualQuery = queryBuilder.build(data);

        assertEquals(expectedQuery, actualQuery);
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Table(name = "dummyData")
    static class DummyData {
        @UpdateBy
        private Integer dummyID;
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
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    static class DummyAddress {
        @UpdateBy
        private String addressString;
        private DummyAmount dummyAmount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    static class DummyAmount {
        private String currency;
        private Double amount;
    }
}
