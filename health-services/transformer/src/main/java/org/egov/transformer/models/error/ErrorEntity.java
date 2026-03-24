package org.egov.transformer.models.error;

import com.fasterxml.jackson.annotation.JsonProperty;


public class ErrorEntity {
    @JsonProperty("exception")
    private Exception exception = null;
    @JsonProperty("type")
    private ErrorType errorType = null;
    @JsonProperty("errorCode")
    private String errorCode = null;
    @JsonProperty("errorMessage")
    private String errorMessage = null;
    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    public static ErrorEntityBuilder builder() {
        return new ErrorEntityBuilder();
    }

    public Exception getException() {
        return this.exception;
    }

    public ErrorType getErrorType() {
        return this.errorType;
    }

    public String getErrorCode() {
        return this.errorCode;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public Object getAdditionalDetails() {
        return this.additionalDetails;
    }

    @JsonProperty("exception")
    public void setException(final Exception exception) {
        this.exception = exception;
    }

    @JsonProperty("type")
    public void setErrorType(final ErrorType errorType) {
        this.errorType = errorType;
    }

    @JsonProperty("errorCode")
    public void setErrorCode(final String errorCode) {
        this.errorCode = errorCode;
    }

    @JsonProperty("errorMessage")
    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @JsonProperty("additionalDetails")
    public void setAdditionalDetails(final Object additionalDetails) {
        this.additionalDetails = additionalDetails;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof ErrorEntity)) {
            return false;
        } else {
            ErrorEntity other = (ErrorEntity) o;
            if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$exception = this.getException();
                Object other$exception = other.getException();
                if (this$exception == null) {
                    if (other$exception != null) {
                        return false;
                    }
                } else if (!this$exception.equals(other$exception)) {
                    return false;
                }

                Object this$errorType = this.getErrorType();
                Object other$errorType = other.getErrorType();
                if (this$errorType == null) {
                    if (other$errorType != null) {
                        return false;
                    }
                } else if (!this$errorType.equals(other$errorType)) {
                    return false;
                }

                Object this$errorCode = this.getErrorCode();
                Object other$errorCode = other.getErrorCode();
                if (this$errorCode == null) {
                    if (other$errorCode != null) {
                        return false;
                    }
                } else if (!this$errorCode.equals(other$errorCode)) {
                    return false;
                }

                Object this$errorMessage = this.getErrorMessage();
                Object other$errorMessage = other.getErrorMessage();
                if (this$errorMessage == null) {
                    if (other$errorMessage != null) {
                        return false;
                    }
                } else if (!this$errorMessage.equals(other$errorMessage)) {
                    return false;
                }

                Object this$additionalDetails = this.getAdditionalDetails();
                Object other$additionalDetails = other.getAdditionalDetails();
                if (this$additionalDetails == null) {
                    if (other$additionalDetails != null) {
                        return false;
                    }
                } else if (!this$additionalDetails.equals(other$additionalDetails)) {
                    return false;
                }

                return true;
            }
        }
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ErrorEntity;
    }

    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $exception = this.getException();
        result = result * 59 + ($exception == null ? 43 : $exception.hashCode());
        Object $errorType = this.getErrorType();
        result = result * 59 + ($errorType == null ? 43 : $errorType.hashCode());
        Object $errorCode = this.getErrorCode();
        result = result * 59 + ($errorCode == null ? 43 : $errorCode.hashCode());
        Object $errorMessage = this.getErrorMessage();
        result = result * 59 + ($errorMessage == null ? 43 : $errorMessage.hashCode());
        Object $additionalDetails = this.getAdditionalDetails();
        result = result * 59 + ($additionalDetails == null ? 43 : $additionalDetails.hashCode());
        return result;
    }

    public String toString() {
        return "ErrorEntity(exception=" + this.getException() + ", errorType=" + this.getErrorType() + ", errorCode=" + this.getErrorCode() + ", errorMessage=" + this.getErrorMessage() + ", additionalDetails=" + this.getAdditionalDetails() + ")";
    }

    public ErrorEntity(final Exception exception, final ErrorType errorType, final String errorCode, final String errorMessage, final Object additionalDetails) {
        this.exception = exception;
        this.errorType = errorType;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.additionalDetails = additionalDetails;
    }

    public ErrorEntity() {
    }

    public static class ErrorEntityBuilder {
        private Exception exception;
        private ErrorType errorType;
        private String errorCode;
        private String errorMessage;
        private Object additionalDetails;

        ErrorEntityBuilder() {
        }

        @JsonProperty("exception")
        public ErrorEntityBuilder exception(final Exception exception) {
            this.exception = exception;
            return this;
        }

        @JsonProperty("type")
        public ErrorEntityBuilder errorType(final ErrorType errorType) {
            this.errorType = errorType;
            return this;
        }

        @JsonProperty("errorCode")
        public ErrorEntityBuilder errorCode(final String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        @JsonProperty("errorMessage")
        public ErrorEntityBuilder errorMessage(final String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        @JsonProperty("additionalDetails")
        public ErrorEntityBuilder additionalDetails(final Object additionalDetails) {
            this.additionalDetails = additionalDetails;
            return this;
        }

        public ErrorEntity build() {
            return new ErrorEntity(this.exception, this.errorType, this.errorCode, this.errorMessage, this.additionalDetails);
        }

        public String toString() {
            return "ErrorEntity.ErrorEntityBuilder(exception=" + this.exception + ", errorType=" + this.errorType + ", errorCode=" + this.errorCode + ", errorMessage=" + this.errorMessage + ", additionalDetails=" + this.additionalDetails + ")";
        }
    }
}
