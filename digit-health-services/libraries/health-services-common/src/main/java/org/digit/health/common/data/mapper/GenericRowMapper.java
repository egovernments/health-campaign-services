package org.digit.health.common.data.mapper;

import org.digit.health.common.utils.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * GenericRowMapper is an implementation of RowMapper<T>. It is used for mapping ResultSet from sql query to List<T>
 * GenericRowMapper throws SQLException if there is an error related to ResultSet or db or query.
 * GenericRowMapper throws RuntimeException whenever there is an error while converting resultset to object.
 *
 * Requirements to use GenericRowMapper
 * Column name and member variable name must be similar. for example columnName "price" will be mapped to member variable named "price"
 * Column type and member variable type must be compatible. for example columnType "int" should be mapped to numeric data type, if you try to map it string etc. IllegalArgumentException will be thrown.
 * DefaultConstructor needs to be present for the object. DefaultConstructor is a constructor with no arguments.
 *
 * Table Employee(id int, name varchar(50), salary int)
 * class Emp{ //class name be anything.
 *     private int id; //this will be mapped with id from Employee table.
 *     private int someId; //this will not be mapped to anything, because its name does not correspond with any table column name.
 *     private String name; //this will be mapped with name from Employee table. Notice String and varchar are compatible data types. If you change String to int, GenericRowMapper will throw RunTimeException.
 *     int salary;
 * }
 *
 * Usage:
 *  List<Emp> emps = jdbcQueryTemplate.query("select * from Employee", new GenericRowMapper(Emp.class))
 */
public class GenericRowMapper <T> implements RowMapper<T> {
    private Class<T> mappedClass;

    public GenericRowMapper(Class<T> mappedClass) {
        this.mappedClass = mappedClass;
    }

    private Object mapRow(Class<?> clazz, Map<String, Object> row) throws IllegalAccessException, IllegalArgumentException {
        Object parentObject = BeanUtils.instantiateClass(clazz);
        Field[] fields = parentObject.getClass().getDeclaredFields();
        for (Field f : fields) {
            f.setAccessible(true);
            if (!f.getType().isPrimitive() && !ObjectUtils.isWrapper(f)) {
                Object nestedObject = mapRow(f.getType(), row);
                f.set(parentObject, nestedObject);
            }else{
                Object value = row.get(f.getName());
                f.set(parentObject, value);
            }
        }
        return parentObject;
    }

    private Map<String, Object> getMap(ResultSet rs) throws SQLException, ClassNotFoundException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        Map<String, Object>  row = new HashMap(columnCount);

        for (int index = 1; index <= columnCount; index++) {
            String column = JdbcUtils.lookupColumnName(metaData, index);
            Object value = JdbcUtils.getResultSetValue(rs, index, Class.forName(metaData.getColumnClassName(index)));
            row.put(column, value);
        }
        return row;
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException{
        T instance;
        try {
            Map<String, Object>  row = getMap(rs);
            instance = (T) mapRow(this.mappedClass, row);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }

        return instance;
    }
}
