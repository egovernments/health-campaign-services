package org.egov.processor.web.models;

import java.util.List;

import org.springframework.validation.annotation.Validated;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a response containing locale-specific messages.
 * This class is annotated for validation and includes Lombok annotations for generating getters, setters, constructors, and builder.
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LocaleResponse {
	 // Field representing a list of Locale messages 
    private List<Locale> messages;;
}
