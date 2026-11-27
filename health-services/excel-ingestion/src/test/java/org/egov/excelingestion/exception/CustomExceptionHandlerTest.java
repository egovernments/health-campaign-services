package org.egov.excelingestion.exception;

import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomExceptionHandlerTest {

    private final CustomExceptionHandler handler = new CustomExceptionHandler();

    @Test
    void directErrorDoesNotRepeatMessage() {
        CustomException error = assertThrows(CustomException.class,
                () -> handler.throwCustomException("CODE", "Localized message"));

        assertEquals("CODE", error.getCode());
        assertEquals("Localized message", error.getMessage());
    }

    @Test
    void originalExceptionIsKeptOutOfUserMessage() {
        CustomException error = assertThrows(CustomException.class,
                () -> handler.throwCustomException("CODE", "Localized message",
                        new IllegalStateException("Internal details")));

        assertEquals("Localized message", error.getMessage());
    }
}
