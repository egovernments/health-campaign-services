package org.egov.healthnotification.util;

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.egov.healthnotification.Constants;
import org.junit.jupiter.api.Test;

class HealthNotificationUtilsTest {

    // ── replacePlaceholders (String map) ──

    @Test
    void replacePlaceholders_stringMap_replacesAll() {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{HouseholdHeadName}", "John Doe");
        placeholders.put("{MobileNumber}", "+234123456");

        String result = HealthNotificationUtils.replacePlaceholders(
                "Hello {HouseholdHeadName}, your number is {MobileNumber}", placeholders);

        assertEquals("Hello John Doe, your number is +234123456", result);
    }

    @Test
    void replacePlaceholders_stringMap_nullValue_replacesWithEmpty() {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{Name}", null);

        String result = HealthNotificationUtils.replacePlaceholders("Hello {Name}", placeholders);

        assertEquals("Hello ", result);
    }

    // ── replacePlaceholders (Object map, with date formatting) ──

    @Test
    void replacePlaceholders_objectMap_replacesStringValues() {
        Map<String, Object> context = new HashMap<>();
        context.put("Sending_Facility_Name", "Warehouse A");
        context.put("quantity_of_sku", "50 ITN Nets");

        ZoneId zoneId = ZoneId.of("UTC");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String result = HealthNotificationUtils.replacePlaceholders(
                "{Sending_Facility_Name} issued {quantity_of_sku}", context, zoneId, formatter);

        assertEquals("Warehouse A issued 50 ITN Nets", result);
    }

    @Test
    void replacePlaceholders_objectMap_formatsDatePlaceholder() {
        Map<String, Object> context = new HashMap<>();
        // 2024-01-15 00:00:00 UTC in epoch millis
        context.put("DeliveryDate", 1705276800000L);

        ZoneId zoneId = ZoneId.of("UTC");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String result = HealthNotificationUtils.replacePlaceholders(
                "Delivery on {DeliveryDate}", context, zoneId, formatter);

        assertEquals("Delivery on 2024-01-15", result);
    }

    @Test
    void replacePlaceholders_objectMap_nonDateLong_notFormatted() {
        Map<String, Object> context = new HashMap<>();
        context.put("quantity", 50L);

        ZoneId zoneId = ZoneId.of("UTC");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String result = HealthNotificationUtils.replacePlaceholders(
                "Qty: {quantity}", context, zoneId, formatter);

        // "quantity" does not contain "Date", so it stays as-is
        assertEquals("Qty: 50", result);
    }

    // ── isEpochTimestamp ──

    @Test
    void isEpochTimestamp_longValue_returnsTrue() {
        assertTrue(HealthNotificationUtils.isEpochTimestamp(1705276800000L));
    }

    @Test
    void isEpochTimestamp_intValue_returnsTrue() {
        assertTrue(HealthNotificationUtils.isEpochTimestamp(1705276800));
    }

    @Test
    void isEpochTimestamp_numericString_returnsTrue() {
        assertTrue(HealthNotificationUtils.isEpochTimestamp("1705276800000"));
    }

    @Test
    void isEpochTimestamp_nonNumericString_returnsFalse() {
        assertFalse(HealthNotificationUtils.isEpochTimestamp("not-a-number"));
    }

    @Test
    void isEpochTimestamp_otherObject_returnsFalse() {
        assertFalse(HealthNotificationUtils.isEpochTimestamp(3.14));
    }

    // ── formatDate ──

    @Test
    void formatDate_validTimestamp_returnsFormattedDate() {
        String result = HealthNotificationUtils.formatDate(1705276800000L);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void formatDate_null_returnsEmpty() {
        assertEquals("", HealthNotificationUtils.formatDate(null));
    }

    // ── determineRecipientMobileNumber ──

    @Test
    void determineRecipientMobileNumber_mobilePresent_returnsMobile() {
        Map<String, String> details = Map.of(
                Constants.FIELD_MOBILE_NUMBER, "+234123456",
                Constants.FIELD_ALT_CONTACT_NUMBER, "+234654321");

        assertEquals("+234123456", HealthNotificationUtils.determineRecipientMobileNumber(details));
    }

    @Test
    void determineRecipientMobileNumber_mobileBlank_returnsAlt() {
        Map<String, String> details = new HashMap<>();
        details.put(Constants.FIELD_MOBILE_NUMBER, "  ");
        details.put(Constants.FIELD_ALT_CONTACT_NUMBER, "+234654321");

        assertEquals("+234654321", HealthNotificationUtils.determineRecipientMobileNumber(details));
    }

    @Test
    void determineRecipientMobileNumber_bothAbsent_returnsNull() {
        Map<String, String> details = new HashMap<>();

        assertNull(HealthNotificationUtils.determineRecipientMobileNumber(details));
    }

    // ── formatEpochToDate ──

    @Test
    void formatEpochToDate_validLong_formatsCorrectly() {
        ZoneId zoneId = ZoneId.of("UTC");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String result = HealthNotificationUtils.formatEpochToDate(1705276800000L, zoneId, formatter);

        assertEquals("2024-01-15", result);
    }

    @Test
    void formatEpochToDate_integerValue_formatsCorrectly() {
        ZoneId zoneId = ZoneId.of("UTC");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Integer epoch (seconds-like) — still treated as millis
        String result = HealthNotificationUtils.formatEpochToDate(1705276800, zoneId, formatter);

        assertNotNull(result);
    }

    @Test
    void formatEpochToDate_stringValue_formatsCorrectly() {
        ZoneId zoneId = ZoneId.of("UTC");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String result = HealthNotificationUtils.formatEpochToDate("1705276800000", zoneId, formatter);

        assertEquals("2024-01-15", result);
    }

    @Test
    void formatEpochToDate_invalidValue_returnsOriginalString() {
        ZoneId zoneId = ZoneId.of("UTC");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String result = HealthNotificationUtils.formatEpochToDate("not-a-number", zoneId, formatter);

        assertEquals("not-a-number", result);
    }
}