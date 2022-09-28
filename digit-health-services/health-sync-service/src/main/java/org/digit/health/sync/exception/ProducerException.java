package org.digit.health.sync.exception;

public class ProducerException extends RuntimeException {
    public ProducerException(String message, Throwable cause) {super(message, cause);}

    public ProducerException(String message) {
        super(message);
    }
}
