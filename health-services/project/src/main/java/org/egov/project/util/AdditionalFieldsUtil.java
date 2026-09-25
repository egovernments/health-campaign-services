package org.egov.project.util;

import java.util.ArrayList;
import java.util.List;

import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;

/**
 * Reads and writes a single key inside an entity's additionalFields.
 *
 * Every method is null safe and returns the AdditionalFields to assign back, so callers do
 * entity.setAdditionalFields(AdditionalFieldsUtil.upsertField(entity.getAdditionalFields(), ...)).
 * The incoming list may be immutable, so it is always copied before being mutated.
 */
public final class AdditionalFieldsUtil {

    private AdditionalFieldsUtil() {
    }

    /**
     * @return the value of the first field with that key, or null when absent
     */
    public static String getFieldValue(AdditionalFields additionalFields, String key) {
        if (additionalFields == null || additionalFields.getFields() == null) {
            return null;
        }
        return additionalFields.getFields().stream()
                .filter(field -> field != null && key.equals(field.getKey()))
                .map(Field::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Replaces every entry with that key with exactly one, so a row that arrived with duplicates
     * is collapsed rather than appended to. Creates the AdditionalFields when it is null.
     */
    public static AdditionalFields upsertField(AdditionalFields additionalFields, String schema, String key,
                                               String value) {
        AdditionalFields target = additionalFields;
        if (target == null) {
            target = AdditionalFields.builder().schema(schema).version(1).fields(new ArrayList<>()).build();
        }
        List<Field> fields = target.getFields() == null ? new ArrayList<>() : new ArrayList<>(target.getFields());
        fields.removeIf(field -> field == null || key.equals(field.getKey()));
        fields.add(Field.builder().key(key).value(value).build());
        target.setFields(fields);
        return target;
    }

    /**
     * Removes every entry with that key and leaves all other keys, the schema and the version
     * untouched.
     */
    public static AdditionalFields removeField(AdditionalFields additionalFields, String key) {
        if (additionalFields == null || additionalFields.getFields() == null) {
            return additionalFields;
        }
        List<Field> fields = new ArrayList<>(additionalFields.getFields());
        fields.removeIf(field -> field == null || key.equals(field.getKey()));
        additionalFields.setFields(fields);
        return additionalFields;
    }
}
