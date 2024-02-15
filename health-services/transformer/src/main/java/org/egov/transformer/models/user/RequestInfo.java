//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.egov.transformer.models.user;


import org.egov.common.contract.request.PlainAccessRequest;
import org.egov.common.contract.request.User;
public class RequestInfo {
    private String apiId;
    private String ver;
    private Long ts;
    private String action;
    private String did;
    private String key;
    private String msgId;
    private String authToken;
    private String correlationId;
    private PlainAccessRequest plainAccessRequest;
    private User userInfo;

    public static RequestInfoBuilder builder() {
        return new RequestInfoBuilder();
    }

    public String getApiId() {
        return this.apiId;
    }

    public String getVer() {
        return this.ver;
    }

    public Long getTs() {
        return this.ts;
    }

    public String getAction() {
        return this.action;
    }

    public String getDid() {
        return this.did;
    }

    public String getKey() {
        return this.key;
    }

    public String getMsgId() {
        return this.msgId;
    }

    public String getAuthToken() {
        return this.authToken;
    }

    public String getCorrelationId() {
        return this.correlationId;
    }

    public PlainAccessRequest getPlainAccessRequest() {
        return this.plainAccessRequest;
    }

    public User getUserInfo() {
        return this.userInfo;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public void setVer(String ver) {
        this.ver = ver;
    }

    public void setTs(Long ts) {
        this.ts = ts;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setDid(String did) {
        this.did = did;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public void setPlainAccessRequest(PlainAccessRequest plainAccessRequest) {
        this.plainAccessRequest = plainAccessRequest;
    }

    public void setUserInfo(User userInfo) {
        this.userInfo = userInfo;
    }

    public RequestInfo() {
    }

    public RequestInfo(String apiId, String ver, Long ts, String action, String did, String key, String msgId, String authToken, String correlationId, PlainAccessRequest plainAccessRequest, User userInfo) {
        this.apiId = apiId;
        this.ver = ver;
        this.ts = ts;
        this.action = action;
        this.did = did;
        this.key = key;
        this.msgId = msgId;
        this.authToken = authToken;
        this.correlationId = correlationId;
        this.plainAccessRequest = plainAccessRequest;
        this.userInfo = userInfo;
    }

    public String toString() {
        return "RequestInfo(apiId=" + this.getApiId() + ", ver=" + this.getVer() + ", ts=" + this.getTs() + ", action=" + this.getAction() + ", did=" + this.getDid() + ", key=" + this.getKey() + ", msgId=" + this.getMsgId() + ", authToken=" + this.getAuthToken() + ", correlationId=" + this.getCorrelationId() + ", plainAccessRequest=" + this.getPlainAccessRequest() + ", userInfo=" + this.getUserInfo() + ")";
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof RequestInfo)) {
            return false;
        } else {
            RequestInfo other = (RequestInfo)o;
            if (!other.canEqual(this)) {
                return false;
            } else {
                label143: {
                    Object this$ts = this.getTs();
                    Object other$ts = other.getTs();
                    if (this$ts == null) {
                        if (other$ts == null) {
                            break label143;
                        }
                    } else if (this$ts.equals(other$ts)) {
                        break label143;
                    }

                    return false;
                }

                Object this$apiId = this.getApiId();
                Object other$apiId = other.getApiId();
                if (this$apiId == null) {
                    if (other$apiId != null) {
                        return false;
                    }
                } else if (!this$apiId.equals(other$apiId)) {
                    return false;
                }

                Object this$ver = this.getVer();
                Object other$ver = other.getVer();
                if (this$ver == null) {
                    if (other$ver != null) {
                        return false;
                    }
                } else if (!this$ver.equals(other$ver)) {
                    return false;
                }

                label122: {
                    Object this$action = this.getAction();
                    Object other$action = other.getAction();
                    if (this$action == null) {
                        if (other$action == null) {
                            break label122;
                        }
                    } else if (this$action.equals(other$action)) {
                        break label122;
                    }

                    return false;
                }

                label115: {
                    Object this$did = this.getDid();
                    Object other$did = other.getDid();
                    if (this$did == null) {
                        if (other$did == null) {
                            break label115;
                        }
                    } else if (this$did.equals(other$did)) {
                        break label115;
                    }

                    return false;
                }

                Object this$key = this.getKey();
                Object other$key = other.getKey();
                if (this$key == null) {
                    if (other$key != null) {
                        return false;
                    }
                } else if (!this$key.equals(other$key)) {
                    return false;
                }

                Object this$msgId = this.getMsgId();
                Object other$msgId = other.getMsgId();
                if (this$msgId == null) {
                    if (other$msgId != null) {
                        return false;
                    }
                } else if (!this$msgId.equals(other$msgId)) {
                    return false;
                }

                label94: {
                    Object this$authToken = this.getAuthToken();
                    Object other$authToken = other.getAuthToken();
                    if (this$authToken == null) {
                        if (other$authToken == null) {
                            break label94;
                        }
                    } else if (this$authToken.equals(other$authToken)) {
                        break label94;
                    }

                    return false;
                }

                label87: {
                    Object this$correlationId = this.getCorrelationId();
                    Object other$correlationId = other.getCorrelationId();
                    if (this$correlationId == null) {
                        if (other$correlationId == null) {
                            break label87;
                        }
                    } else if (this$correlationId.equals(other$correlationId)) {
                        break label87;
                    }

                    return false;
                }

                Object this$plainAccessRequest = this.getPlainAccessRequest();
                Object other$plainAccessRequest = other.getPlainAccessRequest();
                if (this$plainAccessRequest == null) {
                    if (other$plainAccessRequest != null) {
                        return false;
                    }
                } else if (!this$plainAccessRequest.equals(other$plainAccessRequest)) {
                    return false;
                }

                Object this$userInfo = this.getUserInfo();
                Object other$userInfo = other.getUserInfo();
                if (this$userInfo == null) {
                    if (other$userInfo != null) {
                        return false;
                    }
                } else if (!this$userInfo.equals(other$userInfo)) {
                    return false;
                }

                return true;
            }
        }
    }

    protected boolean canEqual(Object other) {
        return other instanceof RequestInfo;
    }

    public int hashCode() {
        boolean PRIME = true;
        int result = 1;
        Object $ts = this.getTs();
        result = result * 59 + ($ts == null ? 43 : $ts.hashCode());
        Object $apiId = this.getApiId();
        result = result * 59 + ($apiId == null ? 43 : $apiId.hashCode());
        Object $ver = this.getVer();
        result = result * 59 + ($ver == null ? 43 : $ver.hashCode());
        Object $action = this.getAction();
        result = result * 59 + ($action == null ? 43 : $action.hashCode());
        Object $did = this.getDid();
        result = result * 59 + ($did == null ? 43 : $did.hashCode());
        Object $key = this.getKey();
        result = result * 59 + ($key == null ? 43 : $key.hashCode());
        Object $msgId = this.getMsgId();
        result = result * 59 + ($msgId == null ? 43 : $msgId.hashCode());
        Object $authToken = this.getAuthToken();
        result = result * 59 + ($authToken == null ? 43 : $authToken.hashCode());
        Object $correlationId = this.getCorrelationId();
        result = result * 59 + ($correlationId == null ? 43 : $correlationId.hashCode());
        Object $plainAccessRequest = this.getPlainAccessRequest();
        result = result * 59 + ($plainAccessRequest == null ? 43 : $plainAccessRequest.hashCode());
        Object $userInfo = this.getUserInfo();
        result = result * 59 + ($userInfo == null ? 43 : $userInfo.hashCode());
        return result;
    }

    public static class RequestInfoBuilder {
        private String apiId;
        private String ver;
        private Long ts;
        private String action;
        private String did;
        private String key;
        private String msgId;
        private String authToken;
        private String correlationId;
        private PlainAccessRequest plainAccessRequest;
        private User userInfo;

        RequestInfoBuilder() {
        }

        public RequestInfoBuilder apiId(String apiId) {
            this.apiId = apiId;
            return this;
        }

        public RequestInfoBuilder ver(String ver) {
            this.ver = ver;
            return this;
        }

        public RequestInfoBuilder ts(Long ts) {
            this.ts = ts;
            return this;
        }

        public RequestInfoBuilder action(String action) {
            this.action = action;
            return this;
        }

        public RequestInfoBuilder did(String did) {
            this.did = did;
            return this;
        }

        public RequestInfoBuilder key(String key) {
            this.key = key;
            return this;
        }

        public RequestInfoBuilder msgId(String msgId) {
            this.msgId = msgId;
            return this;
        }

        public RequestInfoBuilder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public RequestInfoBuilder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public RequestInfoBuilder plainAccessRequest(PlainAccessRequest plainAccessRequest) {
            this.plainAccessRequest = plainAccessRequest;
            return this;
        }

        public RequestInfoBuilder userInfo(User userInfo) {
            this.userInfo = userInfo;
            return this;
        }

        public RequestInfo build() {
            return new RequestInfo(this.apiId, this.ver, this.ts, this.action, this.did, this.key, this.msgId, this.authToken, this.correlationId, this.plainAccessRequest, this.userInfo);
        }

        public String toString() {
            return "RequestInfo.RequestInfoBuilder(apiId=" + this.apiId + ", ver=" + this.ver + ", ts=" + this.ts + ", action=" + this.action + ", did=" + this.did + ", key=" + this.key + ", msgId=" + this.msgId + ", authToken=" + this.authToken + ", correlationId=" + this.correlationId + ", plainAccessRequest=" + this.plainAccessRequest + ", userInfo=" + this.userInfo + ")";
        }
    }
}
