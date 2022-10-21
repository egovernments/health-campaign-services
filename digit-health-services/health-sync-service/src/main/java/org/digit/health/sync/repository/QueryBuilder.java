package org.digit.health.sync.repository;

public interface QueryBuilder {
    String buildSelectQuery(Object object);
    String buildUpdateQuery(Object object);

}
