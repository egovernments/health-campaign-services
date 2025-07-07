package com.tarento.analytics.exception;

/**
 * Custom exception class to replace the tracer dependency CustomException
 */
public class CustomException extends RuntimeException {
    
    private String code;
    private String message;
    
    public CustomException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    
    public CustomException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    @Override
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}
