package org.egov.individual.util;

import java.util.ArrayList;
import java.util.List;

import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;

/**
 * Helpers for reading and writing a single key inside an {@link AdditionalFields} value.
 * The mutating methods return the AdditionalFields to assign back, since the value may have to be
 * created when the entity did not carry one yet.
 */
public final class AdditionalFieldsUtil {

    private AdditionalFieldsUtil() {
    }

    /**
     * @return the value of the first field with the given key, null when absent
     */
    public static String getFieldValue(AdditionalFields additionalFields, String key) {
        if (additionalFields == null || additionalFields.getFields() == null) {
            return null;
        }
        return additionalFields.getFields().stream()
                .filter(field -> field != null && field.getKey() != null && field.getKey().equals(key))
                .map(Field::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Replaces every entry carrying the key with exactly one entry holding the given value,
     * leaving all other keys, the schema and the version untouched.
     */
    public static AdditionalFields upsertField(AdditionalFields additionalFields, String schema, String key, String value) {
        AdditionalFields target = additionalFields != null ? additionalFields
                : AdditionalFields.builder().schema(schema).version(1).fields(new ArrayList<>()).build();
        // the stored list can be immutable, so always mutate a copy
        List<Field> fields = target.getFields() == null ? new ArrayList<>() : new ArrayList<>(target.getFields());
        fields.removeIf(field -> field != null && field.getKey() != null && field.getKey().equals(key));
        fields.add(Field.builder().key(key).value(value).build());
        target.setFields(fields);
        return target;
    }

    /**
     * Removes every entry carrying the key, leaving all other keys, the schema and the version untouched.
     */
    public static AdditionalFields removeField(AdditionalFields additionalFields, String key) {
        if (additionalFields == null || additionalFields.getFields() == null) {
            return additionalFields;
        }
        List<Field> fields = new ArrayList<>(additionalFields.getFields());
        fields.removeIf(field -> field != null && field.getKey() != null && field.getKey().equals(key));
        additionalFields.setFields(fields);
        return additionalFields;
    }
}
