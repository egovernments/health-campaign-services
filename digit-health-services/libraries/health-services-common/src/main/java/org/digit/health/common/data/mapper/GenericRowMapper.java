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
 * GenericRowMapper is an implementation of RowMapper<T>. It is used for mapping ResultSet from sql query to List<T><p>
 * GenericRowMapper throws SQLException if there is an error related to ResultSet, db or query.<p>
 * GenericRowMapper throws RuntimeException whenever there is an error while converting resultset to object.<p>
 *<p>
 *<p>
 * Requirements to use GenericRowMapper<p>
 * * Column name and member variable name must be similar. For example, columnName "price" will be mapped to member variable named "price"<p>
 * * Column type and member variable type must be compatible. For example, columnType "int" should be mapped to numeric data type, if you try map "int" to string, etc. IllegalArgumentException will be thrown.<p>
 * * Default Constructor needs to be present for the object. Default Constructor is a constructor with no arguments.<p>
 *<p>
 *<p>
 * <b>Example: </b><p>
 * Table Employee(id int, name varchar(50), salary int)<p>
 * class Emp{<p>
 *     private int id; <p>
 *     private int someId;<p>
 *     private String name;<p>
 *     int salary;<p>
 * }<p>
 * <p>
 * <p>
 *<b>Notice: </b> someId will not be mapped because it does not match with any column name from employee table;
 *<p>
 *<p>
 *<p>
 * <b>Usage:</b><p>
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
