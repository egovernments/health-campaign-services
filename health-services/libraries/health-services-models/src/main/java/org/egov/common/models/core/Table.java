package org.egov.common.models.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
// It is used to specify the table name associated with a class.
// When you annotate a class with @Table, it indicates that the class is mapped to a table in the database.
// Used in GenericQueryBuilder
public @interface Table {
    String name() default "";
}
