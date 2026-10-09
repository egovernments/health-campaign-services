package org.egov.inbox.web.model;
import java.util.List;

public class PlainAccessRequest {
    private String recordId;
    private List<String> plainRequestFields;

    public static PlainAccessRequestBuilder builder() {
        return new PlainAccessRequest.PlainAccessRequestBuilder();
    }

    public String getRecordId() {
        return this.recordId;
    }

    public List<String> getPlainRequestFields() {
        return this.plainRequestFields;
    }

    public void setRecordId(final String recordId) {
        this.recordId = recordId;
    }

    public void setPlainRequestFields(final List<String> plainRequestFields) {
        this.plainRequestFields = plainRequestFields;
    }

    public PlainAccessRequest() {
    }

    public PlainAccessRequest(final String recordId, final List<String> plainRequestFields) {
        this.recordId = recordId;
        this.plainRequestFields = plainRequestFields;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof PlainAccessRequest)) {
            return false;
        } else {
            PlainAccessRequest other = (PlainAccessRequest)o;
            if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$recordId = this.getRecordId();
                Object other$recordId = other.getRecordId();
                if (this$recordId == null) {
                    if (other$recordId != null) {
                        return false;
                    }
                } else if (!this$recordId.equals(other$recordId)) {
                    return false;
                }

                Object this$plainRequestFields = this.getPlainRequestFields();
                Object other$plainRequestFields = other.getPlainRequestFields();
                if (this$plainRequestFields == null) {
                    if (other$plainRequestFields != null) {
                        return false;
                    }
                } else if (!this$plainRequestFields.equals(other$plainRequestFields)) {
                    return false;
                }

                return true;
            }
        }
    }

    protected boolean canEqual(final Object other) {
        return other instanceof PlainAccessRequest;
    }

    public int hashCode() {
        boolean PRIME = true;
        int result = 1;
        Object $recordId = this.getRecordId();
        result = result * 59 + ($recordId == null ? 43 : $recordId.hashCode());
        Object $plainRequestFields = this.getPlainRequestFields();
        result = result * 59 + ($plainRequestFields == null ? 43 : $plainRequestFields.hashCode());
        return result;
    }

    public static class PlainAccessRequestBuilder {
        private String recordId;
        private List<String> plainRequestFields;

        PlainAccessRequestBuilder() {
        }

        public PlainAccessRequest.PlainAccessRequestBuilder recordId(final String recordId) {
            this.recordId = recordId;
            return this;
        }

        public PlainAccessRequest.PlainAccessRequestBuilder plainRequestFields(final List<String> plainRequestFields) {
            this.plainRequestFields = plainRequestFields;
            return this;
        }

        public PlainAccessRequest build() {
            return new PlainAccessRequest(this.recordId, this.plainRequestFields);
        }

        public String toString() {
            return "PlainAccessRequest.PlainAccessRequestBuilder(recordId=" + this.recordId + ", plainRequestFields=" + this.plainRequestFields + ")";
        }
    }
}