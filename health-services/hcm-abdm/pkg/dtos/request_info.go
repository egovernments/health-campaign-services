package dtos

import "encoding/json"

type RequestInfo struct {
	ApiID              string              `json:"apiId,omitempty"`
	Ver                string              `json:"ver,omitempty"`
	Ts                 *int64              `json:"ts,omitempty"` // epoch millis
	Action             string              `json:"action,omitempty"`
	Did                string              `json:"did,omitempty"`
	Key                string              `json:"key,omitempty"`
	MsgID              string              `json:"msgId,omitempty"`
	AuthToken          string              `json:"authToken,omitempty"`
	CorrelationID      string              `json:"correlationId,omitempty"`
	PlainAccessRequest *PlainAccessRequest `json:"plainAccessRequest,omitempty"`
	UserInfo           *User               `json:"userInfo,omitempty"`
}

// ---------- PlainAccessRequest ----------
type PlainAccessRequest struct {
	Any map[string]interface{} `json:"-"`
}

func (p *PlainAccessRequest) UnmarshalJSON(b []byte) error {
	var m map[string]interface{}
	if err := json.Unmarshal(b, &m); err != nil {
		return err
	}
	p.Any = m
	return nil
}
func (p PlainAccessRequest) MarshalJSON() ([]byte, error) {
	if p.Any == nil {
		return []byte(`{}`), nil
	}
	return json.Marshal(p.Any)
}

// ---------- User ----------
type User struct {
	ID                *int64                 `json:"id,omitempty"`
	UUID              string                 `json:"uuid,omitempty"`
	UserName          string                 `json:"userName,omitempty"`
	Name              string                 `json:"name,omitempty"`
	MobileNumber      string                 `json:"mobileNumber,omitempty"`
	EmailID           string                 `json:"emailId,omitempty"`
	TenantID          string                 `json:"tenantId,omitempty"`
	Type              string                 `json:"type,omitempty"`
	Roles             []Role                 `json:"roles,omitempty"`
	AdditionalDetails map[string]interface{} `json:"additionalDetails,omitempty"`
}

type Role struct {
	Name     string `json:"name,omitempty"`
	Code     string `json:"code,omitempty"`
	TenantID string `json:"tenantId,omitempty"`
}
