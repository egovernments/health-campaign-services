package org.egov.individual.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.egov.individual.Constants.INDIVIDUAL_SCHEMA;
import static org.egov.individual.Constants.TEAM_CODE_FIELD_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class AdditionalFieldsUtilTest {

    private static Field field(String key, String value) {
        return Field.builder().key(key).value(value).build();
    }

    private static AdditionalFields additionalFields(List<Field> fields) {
        return AdditionalFields.builder().schema(INDIVIDUAL_SCHEMA).version(2).fields(fields).build();
    }

    /**
     * @return how many entries carry the key, so "replaced, not appended" can be asserted
     */
    private static long countOf(AdditionalFields additionalFields, String key) {
        return additionalFields.getFields().stream()
                .filter(field -> field != null && key.equals(field.getKey()))
                .count();
    }

    @Nested
    class GetFieldValue {

        @Test
        void shouldReturnNullWhenAdditionalFieldsIsNull() {
            assertNull(AdditionalFieldsUtil.getFieldValue(null, TEAM_CODE_FIELD_KEY));
        }

        @Test
        void shouldReturnNullWhenFieldsListIsNull() {
            AdditionalFields additionalFields = additionalFields(null);

            assertNull(AdditionalFieldsUtil.getFieldValue(additionalFields, TEAM_CODE_FIELD_KEY));
        }

        @Test
        void shouldReturnNullWhenFieldsListIsEmpty() {
            AdditionalFields additionalFields = additionalFields(new ArrayList<>());

            assertNull(AdditionalFieldsUtil.getFieldValue(additionalFields, TEAM_CODE_FIELD_KEY));
        }

        @Test
        void shouldReturnNullWhenKeyIsAbsent() {
            AdditionalFields additionalFields = additionalFields(new ArrayList<>(
                    Arrays.asList(field("team_mapping_1", "some-mapping"), field("height", "170"))));

            assertNull(AdditionalFieldsUtil.getFieldValue(additionalFields, TEAM_CODE_FIELD_KEY));
        }

        @Test
        void shouldReturnValueWhenKeyIsPresent() {
            AdditionalFields additionalFields = additionalFields(new ArrayList<>(
                    Arrays.asList(field("team_mapping_1", "some-mapping"), field(TEAM_CODE_FIELD_KEY, "TEAM-001"))));

            assertEquals("TEAM-001", AdditionalFieldsUtil.getFieldValue(additionalFields, TEAM_CODE_FIELD_KEY));
        }

        @Test
        void shouldIgnoreEntriesWithNullKeyOrNullEntry() {
            List<Field> fields = new ArrayList<>();
            fields.add(null);
            fields.add(field(null, "orphan-value"));
            fields.add(field(TEAM_CODE_FIELD_KEY, "TEAM-002"));
            AdditionalFields additionalFields = additionalFields(fields);

            assertEquals("TEAM-002", AdditionalFieldsUtil.getFieldValue(additionalFields, TEAM_CODE_FIELD_KEY));
        }

        @Test
        void shouldNotFailOnNullKeyEntryWhenKeyIsAbsent() {
            List<Field> fields = new ArrayList<>();
            fields.add(field(null, "orphan-value"));
            AdditionalFields additionalFields = additionalFields(fields);

            assertNull(AdditionalFieldsUtil.getFieldValue(additionalFields, TEAM_CODE_FIELD_KEY));
        }

        @Test
        void shouldReturnFirstValueWhenKeyIsDuplicated() {
            AdditionalFields additionalFields = additionalFields(new ArrayList<>(
                    Arrays.asList(field(TEAM_CODE_FIELD_KEY, "TEAM-FIRST"), field(TEAM_CODE_FIELD_KEY, "TEAM-SECOND"))));

            assertEquals("TEAM-FIRST", AdditionalFieldsUtil.getFieldValue(additionalFields, TEAM_CODE_FIELD_KEY));
        }
    }

    @Nested
    class UpsertField {

        @Test
        void shouldCreateAdditionalFieldsWhenNull() {
            AdditionalFields result = AdditionalFieldsUtil
                    .upsertField(null, INDIVIDUAL_SCHEMA, TEAM_CODE_FIELD_KEY, "TEAM-001");

            assertNotNull(result);
            assertEquals(INDIVIDUAL_SCHEMA, result.getSchema());
            assertEquals(Integer.valueOf(1), result.getVersion());
            assertEquals(1, result.getFields().size());
            assertEquals(TEAM_CODE_FIELD_KEY, result.getFields().get(0).getKey());
            assertEquals("TEAM-001", result.getFields().get(0).getValue());
        }

        @Test
        void shouldCreateFieldsListWhenNull() {
            AdditionalFields additionalFields = additionalFields(null);

            AdditionalFields result = AdditionalFieldsUtil
                    .upsertField(additionalFields, INDIVIDUAL_SCHEMA, TEAM_CODE_FIELD_KEY, "TEAM-001");

            assertSame(additionalFields, result);
            assertEquals(1, result.getFields().size());
            assertEquals("TEAM-001", AdditionalFieldsUtil.getFieldValue(result, TEAM_CODE_FIELD_KEY));
        }

        @Test
        void shouldReplaceExistingValueInsteadOfAppending() {
            AdditionalFields additionalFields = additionalFields(new ArrayList<>(
                    Arrays.asList(field(TEAM_CODE_FIELD_KEY, "TEAM-OLD"))));

            AdditionalFields result = AdditionalFieldsUtil
                    .upsertField(additionalFields, INDIVIDUAL_SCHEMA, TEAM_CODE_FIELD_KEY, "TEAM-NEW");

            assertEquals(1, countOf(result, TEAM_CODE_FIELD_KEY));
            assertEquals(1, result.getFields().size());
            assertEquals("TEAM-NEW", AdditionalFieldsUtil.getFieldValue(result, TEAM_CODE_FIELD_KEY));
        }

        @Test
        void shouldCollapsePreExistingDuplicatesIntoOneEntry() {
            AdditionalFields additionalFields = additionalFields(new ArrayList<>(
                    Arrays.asList(field(TEAM_CODE_FIELD_KEY, "TEAM-A"),
                            field("team_mapping_1", "some-mapping"),
                            field(TEAM_CODE_FIELD_KEY, "TEAM-B"))));

            AdditionalFields result = AdditionalFieldsUtil
                    .upsertField(additionalFields, INDIVIDUAL_SCHEMA, TEAM_CODE_FIELD_KEY, "TEAM-C");

            assertEquals(1, countOf(result, TEAM_CODE_FIELD_KEY));
            assertEquals("TEAM-C", AdditionalFieldsUtil.getFieldValue(result, TEAM_CODE_FIELD_KEY));
            assertEquals("some-mapping", AdditionalFieldsUtil.getFieldValue(result, "team_mapping_1"));
        }

        @Test
        void shouldNotThrowWhenIncomingFieldsListIsImmutable() {
            AdditionalFields additionalFields = additionalFields(
                    List.of(field("team_mapping_1", "some-mapping"), field(TEAM_CODE_FIELD_KEY, "TEAM-OLD")));

            AdditionalFields result = assertDoesNotThrow(() -> AdditionalFieldsUtil
                    .upsertField(additionalFields, INDIVIDUAL_SCHEMA, TEAM_CODE_FIELD_KEY, "TEAM-NEW"));

            assertEquals(1, countOf(result, TEAM_CODE_FIELD_KEY));
            assertEquals("TEAM-NEW", AdditionalFieldsUtil.getFieldValue(result, TEAM_CODE_FIELD_KEY));
            assertEquals("some-mapping", AdditionalFieldsUtil.getFieldValue(result, "team_mapping_1"));
        }

        @Test
        void shouldNotThrowWhenIncomingFieldsListIsFixedSizeArraysAsList() {
            AdditionalFields additionalFields = additionalFields(
                    Arrays.asList(field("team_mapping_1", "some-mapping")));

            AdditionalFields result = assertDoesNotThrow(() -> AdditionalFieldsUtil
                    .upsertField(additionalFields, INDIVIDUAL_SCHEMA, TEAM_CODE_FIELD_KEY, "TEAM-NEW"));

            assertEquals(2, result.getFields().size());
            assertEquals("TEAM-NEW", AdditionalFieldsUtil.getFieldValue(result, TEAM_CODE_FIELD_KEY));
        }

        @Test
        void shouldKeepEveryOtherKey() {
            AdditionalFields additionalFields = additionalFields(new ArrayList<>(
                    Arrays.asList(field("team_mapping_1", "mapping-1"),
                            field("team_mapping_2", "mapping-2"),
                            field("height", "170"))));

            AdditionalFields result = AdditionalFieldsUtil
                    .upsertField(additionalFields, INDIVIDUAL_SCHEMA, TEAM_CODE_FIELD_KEY, "TEAM-001");

            assertEquals(4, result.getFields().size());
            assertEquals("mapping-1", AdditionalFieldsUtil.getFieldValue(result, "team_mapping_1"));
            assertEquals("mapping-2", AdditionalFieldsUtil.getFieldValue(result, "team_mapping_2"));
            assertEquals("170", AdditionalFieldsUtil.getFieldValue(result, "height"));
            assertEquals("TEAM-001", AdditionalFieldsUtil.getFieldValue(result, TEAM_CODE_FIELD_KEY));
        }

        @Test
        void shouldNotOverwriteSchemaAndVersionOfAnExistingObject() {
            AdditionalFields additionalFields = AdditionalFields.builder()
                    .schema("SomeOtherSchema")
                    .version(7)
                    .fields(new ArrayList<>())
                    .build();

            AdditionalFields result = AdditionalFieldsUtil
                    .upsertField(additionalFields, INDIVIDUAL_SCHEMA, TEAM_CODE_FIELD_KEY, "TEAM-001");

            assertEquals("SomeOtherSchema", result.getSchema());
            assertEquals(Integer.valueOf(7), result.getVersion());
        }
    }

    @Nested
    class RemoveField {

        @Test
        void shouldReturnNullWhenAdditionalFieldsIsNull() {
            assertNull(AdditionalFieldsUtil.removeField(null, TEAM_CODE_FIELD_KEY));
        }

        @Test
        void shouldBeNullSafeOnFieldsList() {
            AdditionalFields additionalFields = additionalFields(null);

            AdditionalFields result = AdditionalFieldsUtil.removeField(additionalFields, TEAM_CODE_FIELD_KEY);

            assertSame(additionalFields, result);
            assertNull(result.getFields());
        }

        @Test
        void shouldBeNoOpWhenKeyIsAbsent() {
            AdditionalFields additionalFields = additionalFields(new ArrayList<>(
                    Arrays.asList(field("team_mapping_1", "mapping-1"), field("height", "170"))));

            AdditionalFields result = AdditionalFieldsUtil.removeField(additionalFields, TEAM_CODE_FIELD_KEY);

            assertEquals(2, result.getFields().size());
            assertEquals("mapping-1", AdditionalFieldsUtil.getFieldValue(result, "team_mapping_1"));
            assertEquals("170", AdditionalFieldsUtil.getFieldValue(result, "height"));
        }

        @Test
        void shouldRemoveEveryEntryWithTheKeyAndKeepTheRest() {
            AdditionalFields additionalFields = additionalFields(new ArrayList<>(
                    Arrays.asList(field(TEAM_CODE_FIELD_KEY, "TEAM-A"),
                            field("team_mapping_1", "mapping-1"),
                            field(TEAM_CODE_FIELD_KEY, "TEAM-B"),
                            field("team_mapping_2", "mapping-2"))));

            AdditionalFields result = AdditionalFieldsUtil.removeField(additionalFields, TEAM_CODE_FIELD_KEY);

            assertNull(AdditionalFieldsUtil.getFieldValue(result, TEAM_CODE_FIELD_KEY));
            assertEquals(0, countOf(result, TEAM_CODE_FIELD_KEY));
            assertEquals(2, result.getFields().size());
            assertEquals("mapping-1", AdditionalFieldsUtil.getFieldValue(result, "team_mapping_1"));
            assertEquals("mapping-2", AdditionalFieldsUtil.getFieldValue(result, "team_mapping_2"));
        }

        @Test
        void shouldKeepSchemaAndVersionIntact() {
            AdditionalFields additionalFields = AdditionalFields.builder()
                    .schema(INDIVIDUAL_SCHEMA)
                    .version(3)
                    .fields(new ArrayList<>(Arrays.asList(field(TEAM_CODE_FIELD_KEY, "TEAM-A"))))
                    .build();

            AdditionalFields result = AdditionalFieldsUtil.removeField(additionalFields, TEAM_CODE_FIELD_KEY);

            assertEquals(INDIVIDUAL_SCHEMA, result.getSchema());
            assertEquals(Integer.valueOf(3), result.getVersion());
            assertTrue(result.getFields().isEmpty());
        }

        @Test
        void shouldNotThrowWhenIncomingFieldsListIsImmutable() {
            AdditionalFields additionalFields = additionalFields(
                    List.of(field(TEAM_CODE_FIELD_KEY, "TEAM-A"), field("team_mapping_1", "mapping-1")));

            AdditionalFields result = assertDoesNotThrow(
                    () -> AdditionalFieldsUtil.removeField(additionalFields, TEAM_CODE_FIELD_KEY));

            assertEquals(1, result.getFields().size());
            assertEquals("mapping-1", AdditionalFieldsUtil.getFieldValue(result, "team_mapping_1"));
        }

        @Test
        void shouldIgnoreEntriesWithNullKeyOrNullEntry() {
            List<Field> fields = new ArrayList<>();
            fields.add(null);
            fields.add(field(null, "orphan-value"));
            fields.add(field(TEAM_CODE_FIELD_KEY, "TEAM-A"));
            AdditionalFields additionalFields = additionalFields(fields);

            AdditionalFields result = assertDoesNotThrow(
                    () -> AdditionalFieldsUtil.removeField(additionalFields, TEAM_CODE_FIELD_KEY));

            assertNull(AdditionalFieldsUtil.getFieldValue(result, TEAM_CODE_FIELD_KEY));
            assertEquals(2, result.getFields().size());
        }
    }
}
