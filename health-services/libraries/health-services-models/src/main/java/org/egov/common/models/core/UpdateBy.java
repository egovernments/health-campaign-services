package org.egov.common.models.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
// This annotation is used to mark specific fields in a class that should be specially considered during update operations
// Used in GenericQueryBuilder
public @interface UpdateBy {
}
