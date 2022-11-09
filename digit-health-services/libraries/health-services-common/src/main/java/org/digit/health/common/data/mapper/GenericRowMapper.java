package org.digit.health.common.data.mapper;

import org.digit.health.common.utils.DataUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;

public class GenericRowMapper <T> implements RowMapper<T> {
    private Class<T> mappedClass;

    public GenericRowMapper(Class<T> mappedClass) {
        this.mappedClass = mappedClass;
    }

    private Object instantiate(Class<?> clazz, HashMap row) throws IllegalAccessException, IllegalArgumentException {
        Object mappedObject = BeanUtils.instantiateClass(clazz);
        Field[] fields = mappedObject.getClass().getDeclaredFields();
        for (Field f : fields) {
            f.setAccessible(true);
            if (!f.getType().isPrimitive() && !DataUtils.isWrapper(f)) {
                Object mappedNestedObject = instantiate(f.getType(), row);
                f.set(mappedObject, mappedNestedObject);
            }else{
                Object value = row.get(f.getName());
                f.set(mappedObject, value);
            }
        }
        return mappedObject;
    }

    private HashMap getMap(ResultSet rs) throws SQLException, ClassNotFoundException {
        ResultSetMetaData meta_data = rs.getMetaData();
        int columnCount = meta_data.getColumnCount();

        HashMap row = new HashMap(columnCount);

        for (int index = 1; index <= columnCount; index++) {
            String column = JdbcUtils.lookupColumnName(meta_data, index);
            Object value = JdbcUtils.getResultSetValue(rs, index, Class.forName(meta_data.getColumnClassName(index)));
            row.put(column, value);
        }
        return row;
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException{
        T instance;
        try {
            HashMap row = getMap(rs);
            instance = (T) instantiate(this.mappedClass, row);
        } catch (SQLException e) {
            throw new SQLException(e.getMessage());
        } catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }

        return instance;
    }
}
