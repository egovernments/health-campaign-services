package digit.service;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PiiSanitizerServiceTest {

    private PiiSanitizerService piiSanitizerService;

    @Before
    public void setUp() {
        piiSanitizerService = new PiiSanitizerService();
    }

    // --- sanitize tests ---

    @Test
    public void testSanitize_aadhaarWithSpaces() {
        String input = "Find records for user with Aadhaar 1234 5678 9012";
        String result = piiSanitizerService.sanitize(input);
        assertFalse(result.contains("1234 5678 9012"));
        assertTrue(result.contains("[AADHAAR_REDACTED]"));
    }

    @Test
    public void testSanitize_aadhaarWithDashes() {
        String input = "Aadhaar number is 1234-5678-9012";
        String result = piiSanitizerService.sanitize(input);
        assertFalse(result.contains("1234-5678-9012"));
        assertTrue(result.contains("[AADHAAR_REDACTED]"));
    }

    @Test
    public void testSanitize_aadhaarContiguous() {
        String input = "Number 123456789012 found";
        String result = piiSanitizerService.sanitize(input);
        assertFalse(result.contains("123456789012"));
        assertTrue(result.contains("[AADHAAR_REDACTED]"));
    }

    @Test
    public void testSanitize_indianPhone() {
        String input = "Call 9876543210 for info";
        String result = piiSanitizerService.sanitize(input);
        assertFalse(result.contains("9876543210"));
        assertTrue(result.contains("[PHONE_REDACTED]"));
    }

    @Test
    public void testSanitize_indianPhoneWithPrefix() {
        String input = "Call +91-9876543210 for info";
        String result = piiSanitizerService.sanitize(input);
        assertFalse(result.contains("9876543210"));
        assertTrue(result.contains("[PHONE_REDACTED]"));
    }

    @Test
    public void testSanitize_email() {
        String input = "Send to user@example.com please";
        String result = piiSanitizerService.sanitize(input);
        assertFalse(result.contains("user@example.com"));
        assertTrue(result.contains("[EMAIL_REDACTED]"));
    }

    @Test
    public void testSanitize_panCard() {
        String input = "PAN is ABCDE1234F";
        String result = piiSanitizerService.sanitize(input);
        assertFalse(result.contains("ABCDE1234F"));
        assertTrue(result.contains("[PAN_REDACTED]"));
    }

    @Test
    public void testSanitize_multiplePii() {
        String input = "User 9876543210 with email test@test.com and PAN ABCDE1234F";
        String result = piiSanitizerService.sanitize(input);
        assertTrue(result.contains("[PHONE_REDACTED]"));
        assertTrue(result.contains("[EMAIL_REDACTED]"));
        assertTrue(result.contains("[PAN_REDACTED]"));
    }

    @Test
    public void testSanitize_noPii() {
        String input = "Show me all administrations in Oyo state";
        String result = piiSanitizerService.sanitize(input);
        assertEquals(input, result);
    }

    @Test
    public void testSanitize_null() {
        assertNull(piiSanitizerService.sanitize(null));
    }

    @Test
    public void testSanitize_empty() {
        assertEquals("", piiSanitizerService.sanitize(""));
    }

    // --- containsPii tests ---

    @Test
    public void testContainsPii_withAadhaar() {
        assertTrue(piiSanitizerService.containsPii("ID 1234 5678 9012"));
    }

    @Test
    public void testContainsPii_withPhone() {
        assertTrue(piiSanitizerService.containsPii("Call 9876543210"));
    }

    @Test
    public void testContainsPii_withEmail() {
        assertTrue(piiSanitizerService.containsPii("Email user@example.com"));
    }

    @Test
    public void testContainsPii_withPan() {
        assertTrue(piiSanitizerService.containsPii("PAN ABCDE1234F"));
    }

    @Test
    public void testContainsPii_noPii() {
        assertFalse(piiSanitizerService.containsPii("{\"query\":{\"bool\":{\"must\":[]}}}"));
    }

    @Test
    public void testContainsPii_null() {
        assertFalse(piiSanitizerService.containsPii(null));
    }
}
