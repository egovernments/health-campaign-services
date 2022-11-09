package org.digit.health.common.data.mapper;

import lombok.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.io.File;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

public class GenericRowMapperTest {


     EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setName("testdb;DATABASE_TO_UPPER=false")
            .setType(EmbeddedDatabaseType.H2).addScript("schema.sql").build();


    NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(db);

    @Test
    @DisplayName("should map query to simple object")
    void shouldMapQueryResulToSimpleObject() throws SQLException {
        List<Employee> employeeList = namedParameterJdbcTemplate.query("SELECT * from employee", new GenericRowMapper(Employee.class));
        assertEquals(employeeList.size(), 3);
        assertEquals(employeeList.get(0).id, 1);
        assertEquals(employeeList.get(0).name, "JON");
    }

    @Test
    @DisplayName("should map query to nested object")
    void shouldMapQueryResulToNestedObject(){
        List<NestedEmployee> employeeList = namedParameterJdbcTemplate.query("SELECT * from employee", new GenericRowMapper(NestedEmployee.class));
        assertEquals(employeeList.size(), 3);
        assertEquals(employeeList.get(0).amount.currency.currency, null);
        assertEquals(employeeList.get(0).amount.price, 1500);
        assertEquals(employeeList.get(1).amount.currency.currency, "INR");
    }
}

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
class Employee{
    int id;
    String name;
}

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
class NestedEmployee{
    int id;
    String name;
    Amount amount;
}

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
class Amount{
    int price;
    Currency currency;
}

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
class Currency{
    String currency;
}

