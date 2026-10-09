package org.egov.common.models.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to group fields in a search model for OR-based query generation.
 *
 * Fields annotated with the same OrGroup value will be combined using OR
 * in the generated SQL WHERE clause instead of the default AND.
 *
 * Example: If senderId and receiverId both have @OrGroup("senderOrReceiver"),
 * the generated clause will be: (senderId=:senderId OR receiverId=:receiverId)
 *
 * If only one field in the group has a value, it is treated as a normal AND condition.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OrGroup {
    String value();
}
