package org.egov.common.data.mapper;

import lombok.Getter;
import lombok.Setter;
import org.egov.common.data.query.annotations.Table;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenericRowMapperTest {


     EmbeddedDatabase db;
     NamedParameterJdbcTemplate namedParameterJdbcTemplate;
     @BeforeEach
     public void setUp(){
         db = new EmbeddedDatabaseBuilder().setName("testdb;DATABASE_TO_UPPER=false")
                 .setType(EmbeddedDatabaseType.H2).addScript("schema.sql").build();
         namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(db);
     }
//    @Test
//    @DisplayName("should map query to simple object")
//    void shouldMapQueryResulToSimpleObject() throws SQLException {
//
//        List<Employee> employeeList = namedParameterJdbcTemplate.query("SELECT * from employee", new GenericRowMapper(Employee.class));
//        assertEquals(employeeList.get(0).getId().intValue(), 1);
//        assertEquals(employeeList.get(0).getName(), "JON");
//    }
//
//    @Test
//    @DisplayName("should map query to nested object")
//    void shouldMapQueryResulToNestedObject(){
//        List<NestedEmployee> employeeList = namedParameterJdbcTemplate.query("SELECT * from employee", new GenericRowMapper(NestedEmployee.class));
//        assertEquals(employeeList.get(0).getAmount().getCurrency().getCurrency(), null);
//        assertEquals(employeeList.get(0).getAmount().getPrice(), 1500, 0);
//        assertEquals(employeeList.get(1).getAmount().getCurrency().getCurrency(), "INR");
//    }
//
//    @Test
//    @DisplayName("should map query to nested object with prepared sql query")
//    void shouldMapPreparedQueryResulToNestedObject(){
//         String query = "SELECT * from employee where currency=:currency";
//        Map<String, Object> queryMap = new HashMap();
//        queryMap.put("currency", "INR");
//        List<NestedEmployee> employeeList = namedParameterJdbcTemplate.query(query, queryMap, new GenericRowMapper(NestedEmployee.class));
//        assertEquals(employeeList.get(0).getId(), 2);
//        assertEquals(employeeList.get(0).getAmount().getPrice(), 1000);
//        assertEquals(employeeList.get(0).getAmount().getCurrency().getCurrency(), "INR");
//    }
//
//    @Test
//    @DisplayName("should throw an exception if datatypes between query result and object are incompatible")
//    void shouldThrowExceptionWhenDataTypeDoNotMatch(){
//        assertThrows(RuntimeException.class, () -> namedParameterJdbcTemplate.query("SELECT * from employee", new GenericRowMapper(DataTypeMisMatch.class)));
//    }
//
//    @Test
//    @DisplayName("should throw an exception if no default constructor is found")
//    void shouldThrowExceptionWhenNoDefaultConstructorIsFound(){
//        assertThrows(RuntimeException.class, () -> namedParameterJdbcTemplate.query("SELECT * from employee", new GenericRowMapper(NoDefaultConstructor.class)));
//    }
//
//    @Test
//    @DisplayName("should map query to simple object")
//    void shouldMapSelectQueryResulToSimpleObjectWithQueryBuilder() throws SQLException, QueryBuilderException {
//         Employee e = new Employee();
//         e.setId(1);
//
//         SelectQueryBuilder selectQueryBuilderqueryBuilder = new SelectQueryBuilder();
//         List<Employee> employeeList = namedParameterJdbcTemplate.query(selectQueryBuilderqueryBuilder.build(e), selectQueryBuilderqueryBuilder.getParamsMap(), new GenericRowMapper(Employee.class));
//
//         assertEquals(employeeList.get(0).getId().intValue(), 1);
//         assertEquals(employeeList.get(0).getName(), "JON");
//    }
}


@Getter
@Setter
@Table(name = "employee")
class Employee{
    private Integer id;
    private String name;
}

@Getter
class NestedEmployee{
    private int id;
    private String name;
    private Amount amount;
}
@Getter
class Amount{
    private int price;
    private Currency currency;
}
@Getter
class Currency{
    private String currency;
}
@Getter
class DataTypeMisMatch{
    private String price;
}

@Getter
class NoDefaultConstructor{
    private int price;
    public NoDefaultConstructor(int price){
        this.price = price;
    }
}

