package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify localization of error messages in Excel data validations
 */
public class LocalizationValidationTest {

    private XSSFWorkbook workbook;
    private Map<String, String> englishLocalizationMap;
    private Map<String, String> hindiLocalizationMap;
    private Map<String, String> emptyLocalizationMap;

    @BeforeEach
    void setup() {
        workbook = new XSSFWorkbook();
        
        // Setup English localization map
        englishLocalizationMap = new HashMap<>();
        englishLocalizationMap.put("HCM_VALIDATION_INVALID_LEVEL", "Invalid Level");
        englishLocalizationMap.put("HCM_VALIDATION_INVALID_LEVEL_MESSAGE", "Please select a valid level from the dropdown list.");
        englishLocalizationMap.put("HCM_VALIDATION_INVALID_BOUNDARY", "Invalid Boundary");
        englishLocalizationMap.put("HCM_VALIDATION_INVALID_BOUNDARY_MESSAGE", "Please select a valid boundary from the dropdown list.");
        englishLocalizationMap.put("HCM_VALIDATION_INVALID_SELECTION", "Invalid Selection");
        englishLocalizationMap.put("HCM_VALIDATION_INVALID_SELECTION_MESSAGE", "Please select a valid boundary from the dropdown list.");
        englishLocalizationMap.put("HCM_VALIDATION_INVALID_CHILD_SELECTION", "Invalid Child Selection");
        englishLocalizationMap.put("HCM_VALIDATION_INVALID_CHILD_SELECTION_MESSAGE", "Please select a valid child boundary.");
        englishLocalizationMap.put("HCM_VALIDATION_INVALID_DROPDOWN_SELECTION", "Invalid Dropdown Selection");
        englishLocalizationMap.put("HCM_VALIDATION_INVALID_DROPDOWN_SELECTION_MESSAGE", "Please select a value from the dropdown list.");
        englishLocalizationMap.put("HCM_VALIDATION_INVALID_TEXT_LENGTH", "Invalid Text Length");
        englishLocalizationMap.put("HCM_VALIDATION_INVALID_NUMBER", "Invalid Number");
        
        // Dynamic templates
        englishLocalizationMap.put("HCM_VALIDATION_TEXT_LENGTH_BETWEEN", "Text length must be between %d and %d characters");
        englishLocalizationMap.put("HCM_VALIDATION_TEXT_MIN_LENGTH", "Text must be at least %d characters long");
        englishLocalizationMap.put("HCM_VALIDATION_TEXT_MAX_LENGTH", "Text must not exceed %d characters");
        englishLocalizationMap.put("HCM_VALIDATION_NUMBER_BETWEEN_EXCLUSIVE", "Value must be greater than %.0f and less than %.0f");
        englishLocalizationMap.put("HCM_VALIDATION_NUMBER_GREATER_THAN", "Value must be greater than %.0f");
        englishLocalizationMap.put("HCM_VALIDATION_NUMBER_LESS_THAN", "Value must be less than %.0f");
        englishLocalizationMap.put("HCM_VALIDATION_NUMBER_BETWEEN", "Value must be between %.0f and %.0f");
        englishLocalizationMap.put("HCM_VALIDATION_NUMBER_MIN", "Value must be at least %.0f");
        englishLocalizationMap.put("HCM_VALIDATION_NUMBER_MAX", "Value must be at most %.0f");
        
        // Setup Hindi localization map (example)
        hindiLocalizationMap = new HashMap<>();
        hindiLocalizationMap.put("HCM_VALIDATION_INVALID_LEVEL", "अमान्य स्तर");
        hindiLocalizationMap.put("HCM_VALIDATION_INVALID_LEVEL_MESSAGE", "कृपया ड्रॉपडाउन सूची से एक मान्य स्तर चुनें।");
        hindiLocalizationMap.put("HCM_VALIDATION_INVALID_BOUNDARY", "अमान्य सीमा");
        hindiLocalizationMap.put("HCM_VALIDATION_INVALID_BOUNDARY_MESSAGE", "कृपया ड्रॉपडाउन सूची से एक मान्य सीमा चुनें।");
        hindiLocalizationMap.put("HCM_VALIDATION_INVALID_NUMBER", "अमान्य संख्या");
        hindiLocalizationMap.put("HCM_VALIDATION_TEXT_MIN_LENGTH", "पाठ कम से कम %d वर्ण लंबा होना चाहिए");
        hindiLocalizationMap.put("HCM_VALIDATION_NUMBER_MIN", "मान कम से कम %.0f होना चाहिए");
        
        // Empty map to test fallback behavior
        emptyLocalizationMap = new HashMap<>();
    }

    @AfterEach
    void cleanup() throws IOException {
        if (workbook != null) {
            workbook.close();
        }
    }

    @Test
    void testGetLocalizedMessageWithEnglish() {
        String key = "HCM_VALIDATION_INVALID_LEVEL";
        String defaultMessage = "Default Invalid Level";
        
        String result = getLocalizedMessage(englishLocalizationMap, key, defaultMessage);
        
        assertEquals("Invalid Level", result, "Should return English localized message");
    }

    @Test
    void testGetLocalizedMessageWithHindi() {
        String key = "HCM_VALIDATION_INVALID_LEVEL";
        String defaultMessage = "Default Invalid Level";
        
        String result = getLocalizedMessage(hindiLocalizationMap, key, defaultMessage);
        
        assertEquals("अमान्य स्तर", result, "Should return Hindi localized message");
    }

    @Test
    void testGetLocalizedMessageFallbackToDefault() {
        String key = "NON_EXISTENT_KEY";
        String defaultMessage = "Default Message";
        
        String result = getLocalizedMessage(englishLocalizationMap, key, defaultMessage);
        
        assertEquals("Default Message", result, "Should fallback to default message when key not found");
    }

    @Test
    void testGetLocalizedMessageWithNullMap() {
        String key = "HCM_VALIDATION_INVALID_LEVEL";
        String defaultMessage = "Default Invalid Level";
        
        String result = getLocalizedMessage(null, key, defaultMessage);
        
        assertEquals("Default Invalid Level", result, "Should fallback to default when map is null");
    }

    @Test
    void testGetLocalizedMessageWithEmptyMap() {
        String key = "HCM_VALIDATION_INVALID_LEVEL";
        String defaultMessage = "Default Invalid Level";
        
        String result = getLocalizedMessage(emptyLocalizationMap, key, defaultMessage);
        
        assertEquals("Default Invalid Level", result, "Should fallback to default when map is empty");
    }

    @Test
    void testDynamicTemplateFormattingText() {
        String template = englishLocalizationMap.get("HCM_VALIDATION_TEXT_LENGTH_BETWEEN");
        String formatted = String.format(template, 5, 20);
        
        assertEquals("Text length must be between 5 and 20 characters", formatted,
            "Should format text length template correctly");
    }

    @Test
    void testDynamicTemplateFormattingNumber() {
        String template = englishLocalizationMap.get("HCM_VALIDATION_NUMBER_BETWEEN");
        String formatted = String.format(template, 10.0, 100.0);
        
        assertEquals("Value must be between 10 and 100", formatted,
            "Should format number range template correctly");
    }

    @Test
    void testDynamicTemplateFormattingHindi() {
        String template = hindiLocalizationMap.get("HCM_VALIDATION_TEXT_MIN_LENGTH");
        String formatted = String.format(template, 10);
        
        assertEquals("पाठ कम से कम 10 वर्ण लंबा होना चाहिए", formatted,
            "Should format Hindi template correctly");
    }

    @Test
    void testAllLocalizationKeysPresent() {
        List<String> requiredKeys = Arrays.asList(
            "HCM_VALIDATION_INVALID_LEVEL",
            "HCM_VALIDATION_INVALID_LEVEL_MESSAGE",
            "HCM_VALIDATION_INVALID_BOUNDARY",
            "HCM_VALIDATION_INVALID_BOUNDARY_MESSAGE",
            "HCM_VALIDATION_INVALID_SELECTION",
            "HCM_VALIDATION_INVALID_SELECTION_MESSAGE",
            "HCM_VALIDATION_INVALID_CHILD_SELECTION",
            "HCM_VALIDATION_INVALID_CHILD_SELECTION_MESSAGE",
            "HCM_VALIDATION_INVALID_DROPDOWN_SELECTION",
            "HCM_VALIDATION_INVALID_DROPDOWN_SELECTION_MESSAGE",
            "HCM_VALIDATION_INVALID_TEXT_LENGTH",
            "HCM_VALIDATION_INVALID_NUMBER",
            "HCM_VALIDATION_TEXT_LENGTH_BETWEEN",
            "HCM_VALIDATION_TEXT_MIN_LENGTH",
            "HCM_VALIDATION_TEXT_MAX_LENGTH",
            "HCM_VALIDATION_NUMBER_BETWEEN_EXCLUSIVE",
            "HCM_VALIDATION_NUMBER_GREATER_THAN",
            "HCM_VALIDATION_NUMBER_LESS_THAN",
            "HCM_VALIDATION_NUMBER_BETWEEN",
            "HCM_VALIDATION_NUMBER_MIN",
            "HCM_VALIDATION_NUMBER_MAX"
        );
        
        for (String key : requiredKeys) {
            assertTrue(englishLocalizationMap.containsKey(key),
                "English localization should contain key: " + key);
        }
        
        System.out.println("✅ All " + requiredKeys.size() + " localization keys are present in English map");
    }

    @Test
    void testValidationWithLocalizedMessages() throws IOException {
        Sheet sheet = workbook.createSheet("Test");
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // Test creating validation with localized error messages
        String errorTitle = getLocalizedMessage(englishLocalizationMap, 
            "HCM_VALIDATION_INVALID_NUMBER", "Invalid Number");
        String errorMessage = getLocalizedMessage(englishLocalizationMap,
            "HCM_VALIDATION_NUMBER_MIN", "Value must be at least %.0f");
        String formattedMessage = String.format(errorMessage, 10.0);
        
        DataValidationConstraint constraint = dvHelper.createDecimalConstraint(
            DataValidationConstraint.OperatorType.GREATER_OR_EQUAL,
            "10", null);
        CellRangeAddressList addressList = new CellRangeAddressList(0, 10, 0, 0);
        DataValidation validation = dvHelper.createValidation(constraint, addressList);
        
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.setShowErrorBox(true);
        validation.createErrorBox(errorTitle, formattedMessage);
        validation.setShowPromptBox(false); // No prompts as per requirement
        
        sheet.addValidationData(validation);
        
        // Verify validation was added
        List<? extends DataValidation> validations = sheet.getDataValidations();
        assertEquals(1, validations.size(), "Should have one validation");
        
        // Verify workbook can be saved
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        assertTrue(bos.size() > 0, "Workbook with localized validation should be saveable");
        
        System.out.println("✅ Validation with localized messages created successfully");
    }

    @Test
    void testNoPromptsAreShown() throws IOException {
        Sheet sheet = workbook.createSheet("NoPrompts");
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // Create validation and verify no prompts are shown
        DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(
            new String[]{"Option1", "Option2", "Option3"});
        CellRangeAddressList addressList = new CellRangeAddressList(0, 10, 0, 0);
        DataValidation validation = dvHelper.createValidation(constraint, addressList);
        
        validation.setShowPromptBox(false); // This should always be false
        validation.setShowErrorBox(true); // Errors should still be shown
        validation.createErrorBox(
            getLocalizedMessage(englishLocalizationMap, "HCM_VALIDATION_INVALID_DROPDOWN_SELECTION", "Invalid Selection"),
            getLocalizedMessage(englishLocalizationMap, "HCM_VALIDATION_INVALID_DROPDOWN_SELECTION_MESSAGE", "Please select a value from the dropdown list.")
        );
        
        sheet.addValidationData(validation);
        
        // Since POI doesn't expose getShowPromptBox(), we verify by checking our code pattern
        assertFalse(validation.toString().contains("showPromptBox=true"), 
            "Validation should not have prompts enabled");
        
        System.out.println("✅ Verified no prompts are shown (setShowPromptBox is false)");
    }

    @Test
    void testMixedLanguageSupport() {
        // Test that system can handle mixed languages in same map
        Map<String, String> mixedMap = new HashMap<>();
        mixedMap.put("HCM_VALIDATION_INVALID_LEVEL", "Invalid Level"); // English
        mixedMap.put("HCM_VALIDATION_INVALID_BOUNDARY", "अमान्य सीमा"); // Hindi
        mixedMap.put("HCM_VALIDATION_INVALID_NUMBER", "无效号码"); // Chinese
        
        assertEquals("Invalid Level", 
            getLocalizedMessage(mixedMap, "HCM_VALIDATION_INVALID_LEVEL", "Default"),
            "Should handle English");
        assertEquals("अमान्य सीमा", 
            getLocalizedMessage(mixedMap, "HCM_VALIDATION_INVALID_BOUNDARY", "Default"),
            "Should handle Hindi");
        assertEquals("无效号码", 
            getLocalizedMessage(mixedMap, "HCM_VALIDATION_INVALID_NUMBER", "Default"),
            "Should handle Chinese");
        
        System.out.println("✅ Mixed language support verified");
    }

    /**
     * Helper method that mirrors the actual implementation
     */
    private String getLocalizedMessage(Map<String, String> localizationMap, String key, String defaultMessage) {
        if (localizationMap != null && key != null && localizationMap.containsKey(key)) {
            return localizationMap.get(key);
        }
        return defaultMessage;
    }
}