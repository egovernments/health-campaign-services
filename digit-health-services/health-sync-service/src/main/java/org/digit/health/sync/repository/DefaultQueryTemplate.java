package org.digit.health.sync.repository;

public class DefaultQueryTemplate {
    public static String select(String tableName){
        return String.format("SELECT * FROM %s", tableName);
    }

    public static String update(String tableName){
        return String.format("UPDATE %s", tableName);
    }
}
