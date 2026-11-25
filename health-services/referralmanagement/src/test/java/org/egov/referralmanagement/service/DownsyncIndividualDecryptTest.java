package org.egov.referralmanagement.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.postgresql.util.PGobject;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.StringWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Downsync of individuals after encryption at rest: decrypt before writing, output unchanged. */
class DownsyncIndividualDecryptTest {

    @Test
    void cipherCheckAcceptsNumericAndUuidKeyIdsAndRejectsPlainText() {
        assertTrue(DownsyncFileGenService.isCipherText("123456|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        assertTrue(DownsyncFileGenService.isCipherText("0f6c7a52-6a8f-4d8b-9b1e-1c2d3e4f5a6b|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        assertFalse(DownsyncFileGenService.isCipherText("9876543210"));
        assertFalse(DownsyncFileGenService.isCipherText("A|B"));
        assertFalse(DownsyncFileGenService.isCipherText("1980-01-01"));
        assertFalse(DownsyncFileGenService.isCipherText(null));
    }

    @Test
    void dateOfBirthIsReadFromDateOrIsoText() {
        assertEquals(LocalDate.of(1980, 1, 2), DownsyncFileGenService.toLocalDate(java.sql.Date.valueOf("1980-01-02")));
        assertEquals(LocalDate.of(1980, 1, 2), DownsyncFileGenService.toLocalDate("1980-01-02"));
        assertThrows(CustomException.class,
                () -> DownsyncFileGenService.toLocalDate("123456|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
    }

    @Test
    void dateOfBirthTextLikeTheIndividualService() {
        // a timestamp text from an old persister config, and a year of more than 4 digits, as the service reads them
        assertEquals(LocalDate.of(1983, 4, 4), DownsyncFileGenService.toLocalDate("1983-04-04 00:00:00+05:30"));
        assertEquals(LocalDate.of(10000, 1, 1), DownsyncFileGenService.toLocalDate("10000-01-01"));
        assertThrows(CustomException.class, () -> DownsyncFileGenService.toLocalDate("1983-02-30"));
        // one unreadable value is written empty instead of failing the whole downsync job
        assertNull(DownsyncFileGenService.readDob("04/04/1983", "ind-1"));
        assertNull(DownsyncFileGenService.readDob("1983-02-30", "ind-1"));
        assertEquals(LocalDate.of(1980, 1, 2), DownsyncFileGenService.readDob("1980-01-02", "ind-1"));
        // still cipher text after decryption is a real failure, not a data problem
        assertThrows(CustomException.class,
                () -> DownsyncFileGenService.readDob("123456|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "ind-1"));
    }
}
