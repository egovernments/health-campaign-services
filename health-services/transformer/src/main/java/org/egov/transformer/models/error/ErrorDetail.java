//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.egov.transformer.models.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.egov.transformer.models.error.ErrorEntity;

import java.util.List;

public class ErrorDetail {
    @JsonProperty("apiDetails")
    private ApiDetails apiDetails = null;
    @JsonProperty("errors")
    private List<ErrorEntity> errors = null;

    public static ErrorDetailBuilder builder() {
        return new ErrorDetailBuilder();
    }

    public ApiDetails getApiDetails() {
        return this.apiDetails;
    }

    public List<ErrorEntity> getErrors() {
        return this.errors;
    }

    @JsonProperty("apiDetails")
    public void setApiDetails(final ApiDetails apiDetails) {
        this.apiDetails = apiDetails;
    }

    @JsonProperty("errors")
    public void setErrors(final List<ErrorEntity> errors) {
        this.errors = errors;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof ErrorDetail)) {
            return false;
        } else {
            ErrorDetail other = (ErrorDetail)o;
            if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$apiDetails = this.getApiDetails();
                Object other$apiDetails = other.getApiDetails();
                if (this$apiDetails == null) {
                    if (other$apiDetails != null) {
                        return false;
                    }
                } else if (!this$apiDetails.equals(other$apiDetails)) {
                    return false;
                }

                Object this$errors = this.getErrors();
                Object other$errors = other.getErrors();
                if (this$errors == null) {
                    if (other$errors != null) {
                        return false;
                    }
                } else if (!this$errors.equals(other$errors)) {
                    return false;
                }

                return true;
            }
        }
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ErrorDetail;
    }

    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $apiDetails = this.getApiDetails();
        result = result * 59 + ($apiDetails == null ? 43 : $apiDetails.hashCode());
        Object $errors = this.getErrors();
        result = result * 59 + ($errors == null ? 43 : $errors.hashCode());
        return result;
    }

    public String toString() {
        return "ErrorDetail(apiDetails=" + this.getApiDetails() + ", errors=" + this.getErrors() + ")";
    }

    public ErrorDetail(final ApiDetails apiDetails, final List<ErrorEntity> errors) {
        this.apiDetails = apiDetails;
        this.errors = errors;
    }

    public ErrorDetail() {
    }

    public static class ErrorDetailBuilder {
        private ApiDetails apiDetails;
        private List<ErrorEntity> errors;

        ErrorDetailBuilder() {
        }

        @JsonProperty("apiDetails")
        public ErrorDetailBuilder apiDetails(final ApiDetails apiDetails) {
            this.apiDetails = apiDetails;
            return this;
        }

        @JsonProperty("errors")
        public ErrorDetailBuilder errors(final List<ErrorEntity> errors) {
            this.errors = errors;
            return this;
        }

        public ErrorDetail build() {
            return new ErrorDetail(this.apiDetails, this.errors);
        }

        public String toString() {
            return "ErrorDetail.ErrorDetailBuilder(apiDetails=" + this.apiDetails + ", errors=" + this.errors + ")";
        }
    }
}
