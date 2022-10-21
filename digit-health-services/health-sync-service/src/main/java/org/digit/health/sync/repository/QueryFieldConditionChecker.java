package org.digit.health.sync.repository;

import java.lang.reflect.Field;
import java.util.Optional;

public interface QueryFieldConditionChecker {
    boolean check(Field field, Object object) throws IllegalAccessException;

    QueryFieldConditionChecker checkIfFieldIsNotNull = (field, object) -> Optional.ofNullable(field.get(object)).isPresent();
    QueryFieldConditionChecker checkIfUpdateByAnnotationIsPresent = (field, object) -> field.getDeclaredAnnotation(UpdateBy.class) != null;
}
