package org.egov.common.data.query.builder;

import org.egov.common.data.query.annotations.UpdateBy;
import org.egov.common.models.core.Exclude;

import java.lang.reflect.Field;
import java.util.Optional;

public interface QueryFieldChecker {
    boolean check(Field field, Object object) throws IllegalAccessException;

    QueryFieldChecker isNotNull = (field, object) -> Optional.ofNullable(field.get(object)).isPresent();
    QueryFieldChecker isAnnotatedWithUpdateBy = (field, object) -> field.getDeclaredAnnotation(UpdateBy.class) != null;

    // Exclude annotation is now referred from health-services-models
    QueryFieldChecker isNotAnnotatedWithExclude = ((field, object) -> field.getDeclaredAnnotation(Exclude.class) == null);
}
