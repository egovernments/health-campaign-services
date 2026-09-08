package org.egov.processor.web.models;

import org.springframework.validation.annotation.Validated;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a locale-specific message.
 * This class is annotated for validation and includes Lombok annotations for generating getters, setters, constructors, and builder.
 */
@Validated 
@Data 
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Locale {

    // Field representing the code of the locale message
    private String code;

    // Field representing the actual message in the locale
    private String message;

    // Field representing the module to which the message belongs
    private String module;

    // Field representing the locale identifier (e.g., "en_US")
    private String locale;
}
