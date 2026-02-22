package digit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static digit.config.ServiceConstants.*;

@Service
@Slf4j
public class PiiSanitizerService {

    /**
     * Sanitizes PII from the given text by replacing detected patterns with placeholders.
     */
    public String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = text;
        // Order matters: PAN before Aadhaar to avoid partial matches
        result = PAN_PATTERN.matcher(result).replaceAll(PII_REPLACEMENT_PAN);
        result = AADHAAR_PATTERN.matcher(result).replaceAll(PII_REPLACEMENT_AADHAAR);
        result = EMAIL_PATTERN.matcher(result).replaceAll(PII_REPLACEMENT_EMAIL);
        result = INDIAN_PHONE_PATTERN.matcher(result).replaceAll(PII_REPLACEMENT_PHONE);
        return result;
    }

    /**
     * Checks if the given text contains any PII-like patterns.
     * Used as a post-LLM safety check.
     */
    public boolean containsPii(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return AADHAAR_PATTERN.matcher(text).find()
                || INDIAN_PHONE_PATTERN.matcher(text).find()
                || EMAIL_PATTERN.matcher(text).find()
                || PAN_PATTERN.matcher(text).find();
    }
}
